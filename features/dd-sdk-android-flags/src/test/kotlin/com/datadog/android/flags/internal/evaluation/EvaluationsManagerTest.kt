/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsContextSupersededException
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsInvalidContextException
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.FlagsStateHolder
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Fetch
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Sdk
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Trigger
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.NetworkRequestFailedException
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.FirstFlagsLatch
import com.datadog.android.flags.internal.repository.FlagsInstallation
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationsManagerTest {

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M deliver completion off caller W updateEvaluationsForContext() {caller drains reconciling}`(
        mode: ExperimentationOptOutMode
    ) {
        // Given
        val fetchExecutor = Executors.newSingleThreadExecutor()
        val callbackExecutor = Executors.newSingleThreadExecutor()
        val readerEntered = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val fetchFinished = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val callbackThread = AtomicReference<Thread>()
        val caller = Thread.currentThread()
        val calls = AtomicInteger()
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() { callbackThread.set(Thread.currentThread()); completed.countDown() }
            override fun onFailure(
                error: Throwable
            ) { callbackThread.set(Thread.currentThread()); completed.countDown() }
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), any(), any())).thenAnswer {
            readerEntered.countDown()
            check(releaseReader.await(5, TimeUnit.SECONDS))
            "response"
        }
        whenever(mockPrecomputeMapper.map(eq("response"), anyOrNull()))
            .thenReturn(PrecomputedAssignments(emptyMap(), null))
        whenever(mockFlagsStateManager.updateState(any())).thenAnswer {
            if (it.getArgument<FlagsClientState>(0) == FlagsClientState.Reconciling && calls.incrementAndGet() == 2) {
                releaseReader.countDown()
                fetchExecutor.submit { fetchFinished.countDown() }
                check(fetchFinished.await(5, TimeUnit.SECONDS))
            }
            Unit
        }
        val manager = EvaluationsManager(
            mockSdkCore, fetchExecutor, mockInternalLogger, mockFlagsRepository,
            mockAssignmentsDownloader, mockPrecomputeMapper, mockFlagsStateManager,
            null, { _, _ -> {} }, ExperimentationOptOut(mode), callbackExecutor
        )

        try {
            // When
            manager.updateEvaluationsForContext(EvaluationContext("first-subject"), callback)
            assertThat(readerEntered.await(5, TimeUnit.SECONDS)).isTrue()
            manager.updateEvaluationsForContext(EvaluationContext("second-subject"))

            // Then
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(callbackThread.get()).isNotSameAs(caller)
        } finally {
            releaseReader.countDown()
            fetchExecutor.shutdownNow()
            callbackExecutor.shutdownNow()
        }
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M deliver timeout while fetch is blocked W updateEvaluationsForContext()`(mode: ExperimentationOptOutMode) {
        // Given
        val fetchExecutor = Executors.newSingleThreadExecutor()
        val callbackExecutor = Executors.newSingleThreadExecutor()
        val readerEntered = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        var timeout: (() -> Unit)? = null
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), any(), any())).thenAnswer {
            readerEntered.countDown()
            check(releaseReader.await(5, TimeUnit.SECONDS))
            null
        }
        val manager = EvaluationsManager(
            mockSdkCore, fetchExecutor, mockInternalLogger, mockFlagsRepository,
            mockAssignmentsDownloader, mockPrecomputeMapper, mockFlagsStateManager,
            100, { _, action -> timeout = action; {} }, ExperimentationOptOut(mode), callbackExecutor
        )
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = Unit
            override fun onFailure(error: Throwable) { failure.set(error); completed.countDown() }
        }

        try {
            // When
            manager.updateEvaluationsForContext(EvaluationContext("subject"), callback)
            assertThat(readerEntered.await(5, TimeUnit.SECONDS)).isTrue()
            checkNotNull(timeout).invoke()

            // Then
            assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue()
            assertThat(failure.get()).isInstanceOf(FlagsInitializationTimeoutException::class.java)
            assertThat(releaseReader.count).isEqualTo(1)
        } finally {
            releaseReader.countDown()
            fetchExecutor.shutdownNow()
            callbackExecutor.shutdownNow()
        }
    }

    @Test
    fun `M fail with FlagsInvalidContextException W updateEvaluationsForContext() { invalid opt-out timestamp }`() {
        // Given
        val callback = mock<EvaluationContextCallback>()
        val context = EvaluationContext(fakeTargetingKey, mapOf(ExperimentationOptOut.ATTRIBUTE to "junk"))
        val manager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} },
            experimentationOptOut = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
        )

        // When
        manager.updateEvaluationsForContext(context, callback)

        // Then
        argumentCaptor<Throwable> {
            verify(callback).onFailure(capture())
            assertThat(firstValue)
                .isInstanceOf(FlagsInvalidContextException::class.java)
                .hasMessage(ExperimentationOptOut.INVALID_CONTEXT_MESSAGE)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Error(firstValue))
        }
        verifyNoInteractions(mockAssignmentsDownloader)
    }

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    @Mock
    lateinit var mockAssignmentsDownloader: PrecomputedAssignmentsReader

    @Mock
    lateinit var mockPrecomputeMapper: PrecomputeMapper

    @Mock
    lateinit var mockFlagsStateManager: FlagsStateManager

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockFlagsFeatureScope: FeatureScope

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeAttributeKey: String

    @StringForgery
    lateinit var fakeAttributeValue: String

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private lateinit var evaluationsManager: EvaluationsManager

    @Test
    fun `M release request state W updateEvaluationsForContext() {executor rejects fetch}`() {
        doThrow(java.util.concurrent.RejectedExecutionException())
            .whenever(mockExecutorService).execute(any())
        var finished = 0
        evaluationsManager.updateEvaluationsForContext(EvaluationContext.EMPTY, null) { finished++ }
        assertThat(finished).isEqualTo(1)
    }

    @BeforeEach
    fun setUp() {
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        whenever(mockFlagsRepository.firstFlags).thenReturn(FirstFlagsLatch())
        whenever(mockFlagsRepository.cachedFlagsLoaded).thenReturn(FirstFlagsLatch())
        evaluationsManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} },
            experimentationOptOut = ExperimentationOptOut()
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockFlagsFeatureScope
        whenever(mockSdkCore.source) doAnswer { fakeDatadogContext.source }
        whenever(mockSdkCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doAnswer {
            fakeDatadogContext
        }

        whenever(mockFlagsRepository.installFlagsAndContext(any(), any(), anyOrNull())).thenAnswer {
            FlagsInstallation { onInstalled -> onInstalled() }
        }

        // Mock executor to run tasks synchronously for testing
        whenever(mockExecutorService.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }
    }

    @Test
    fun `M retain instance identity and advance logical fetch W updateEvaluationsForContext()`(
        @Forgery fakeFirstContext: EvaluationContext,
        @Forgery fakeSecondContext: EvaluationContext
    ) {
        // Given
        val diagnostics = argumentCaptor<Diagnostics>()

        // When
        evaluationsManager.updateEvaluationsForContext(fakeFirstContext)
        evaluationsManager.updateEvaluationsForContext(fakeSecondContext)

        // Then
        inOrder(mockAssignmentsDownloader) {
            listOf(fakeFirstContext, fakeSecondContext).forEach { expectedContext ->
                verify(mockAssignmentsDownloader).readPrecomputedFlags(
                    eq(expectedContext),
                    eq(fakeDatadogContext),
                    diagnostics.capture()
                )
            }
        }
        val instanceId = diagnostics.firstValue.sdk.instanceId
        val uuid = UUID.fromString(instanceId)
        assertThat(uuid.version()).isEqualTo(7)
        assertThat(uuid.variant()).isEqualTo(2)
        assertThat(diagnostics.allValues).containsExactly(
            Diagnostics(Sdk(instanceId), Fetch(1, Trigger.INITIALIZATION)),
            Diagnostics(Sdk(instanceId), Fetch(2, Trigger.CONTEXT_CHANGE))
        )
    }

    @Test
    fun `M allocate unique sequences W updateEvaluationsForContext() {concurrent calls}`(
        @IntForgery(min = 2, max = 32) fakeCount: Int,
        @Forgery fakeContext: EvaluationContext
    ) {
        // Given
        val executor = Executors.newFixedThreadPool(8)
        val diagnostics = argumentCaptor<Diagnostics>()
        try {
            // When
            val results = executor.invokeAll(
                (0 until fakeCount).map { Callable { evaluationsManager.updateEvaluationsForContext(fakeContext) } },
                10,
                TimeUnit.SECONDS
            )

            // Then
            results.forEach {
                assertThat(it.isDone).isTrue()
                assertThat(it.isCancelled).isFalse()
                it.get()
            }
            verify(mockAssignmentsDownloader, times(fakeCount)).readPrecomputedFlags(
                eq(fakeContext),
                eq(fakeDatadogContext),
                diagnostics.capture()
            )
            assertThat(diagnostics.allValues.map { it.fetch.sequence }).containsExactlyInAnyOrderElementsOf(
                1L..fakeCount.toLong()
            )
            assertThat(diagnostics.allValues.map { it.sdk.instanceId }.toSet()).hasSize(1)
            assertThat(
                diagnostics.allValues.count {
                    it.fetch.trigger == Trigger.INITIALIZATION
                }
            ).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M use independent diagnostics W updateEvaluationsForContext() {distinct managers}`(
        @Forgery fakeContext: EvaluationContext
    ) {
        // Given
        val otherManager = createManager(scheduler = { _, _ -> {} })
        val diagnostics = argumentCaptor<Diagnostics>()

        // When
        evaluationsManager.updateEvaluationsForContext(fakeContext)
        otherManager.updateEvaluationsForContext(fakeContext)

        // Then
        verify(mockAssignmentsDownloader, times(2)).readPrecomputedFlags(
            eq(fakeContext),
            eq(fakeDatadogContext),
            diagnostics.capture()
        )
        assertThat(diagnostics.allValues.map { it.sdk.instanceId }).doesNotHaveDuplicates()
        diagnostics.allValues.forEach {
            assertThat(it).isEqualTo(
                Diagnostics(it.sdk, Fetch(1, Trigger.INITIALIZATION))
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["android", "react-native"])
    fun `M read cache before fetching assignments W updateEvaluationsForContext() {source varies}`(source: String) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(source = source)

        // When
        evaluationsManager.updateEvaluationsForContext(EvaluationContext(fakeTargetingKey, emptyMap()))

        // Then
        inOrder(mockFlagsRepository, mockAssignmentsDownloader) {
            verify(mockFlagsRepository).hasFlags()
            verify(mockAssignmentsDownloader).readPrecomputedFlags(
                eq(EvaluationContext(fakeTargetingKey, emptyMap())),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        }
    }

    @Test
    fun `M release request state W updateEvaluationsForContext() {context dispatch unavailable}`(
        @Forgery fakeContext: EvaluationContext
    ) {
        // Given
        whenever(mockSdkCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doReturn null
        var finished = 0

        // When
        evaluationsManager.updateEvaluationsForContext(fakeContext, null) { finished++ }

        // Then
        assertThat(finished).isEqualTo(1)
        verifyNoInteractions(mockAssignmentsDownloader)
    }

    @Test
    fun `M avoid listener lock inversion W updateEvaluationsForContext() { concurrent registration }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey)
        val policy = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
        val listenerEntered = CountDownLatch(1)
        val publishing = CountDownLatch(1)
        val listenerFinished = CountDownLatch(1)
        val updateFinished = CountDownLatch(1)
        val holder = FlagsStateHolder.create<FlagsClientState, FlagsStateListener>(FlagsClientState.NotReady) {
            onStateChanged(it)
        }
        val observedHolder = object : FlagsStateHolder<FlagsClientState, FlagsStateListener> by holder {
            override fun updateState(newState: FlagsClientState) {
                publishing.countDown()
                holder.updateState(newState)
            }
        }
        val states = FlagsStateManager(observedHolder)
        val manager = EvaluationsManager(
            mockSdkCore, mockExecutorService, mockInternalLogger, mockFlagsRepository,
            mockAssignmentsDownloader, mockPrecomputeMapper, states, null, { _, _ -> {} }, policy
        )
        val listener = Thread {
            states.addListener(object : FlagsStateListener {
                override fun onStateChanged(newState: FlagsClientState) {
                    listenerEntered.countDown()
                    check(publishing.await(2, TimeUnit.SECONDS))
                    policy.resolutionError(context)
                }
            })
            listenerFinished.countDown()
        }.apply { isDaemon = true }
        val updater = Thread {
            manager.updateEvaluationsForContext(context)
            updateFinished.countDown()
        }.apply { isDaemon = true }

        // When
        listener.start()
        check(listenerEntered.await(2, TimeUnit.SECONDS))
        updater.start()
        val listenerDone = listenerFinished.await(2, TimeUnit.SECONDS)
        val updaterDone = updateFinished.await(2, TimeUnit.SECONDS)

        // Then
        assertThat(listenerDone).isTrue()
        assertThat(updaterDone).isTrue()
        listener.join(TimeUnit.SECONDS.toMillis(2))
        updater.join(TimeUnit.SECONDS.toMillis(2))
    }

    @Test
    fun `M release policy lock W updateEvaluationsForContext() { persistence and callbacks }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey)
        val policy = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        val store = mock<DataStoreHandler>()
        val repository = DefaultFlagsRepository(mockSdkCore, fakeTargetingKey, store, persistenceLoadTimeoutMs = 0)
        val manager = EvaluationsManager(
            mockSdkCore, mockExecutorService, mockInternalLogger, repository,
            mockAssignmentsDownloader, mockPrecomputeMapper, mockFlagsStateManager,
            null, { _, _ -> {} }, policy
        )
        val completed = mutableListOf<Boolean>()
        fun probe() {
            val done = CountDownLatch(1)
            val reader = Thread {
                policy.resolutionError(repository.getPrecomputedFlagWithContext(fakeAttributeKey)?.second)
                done.countDown()
            }.apply { isDaemon = true }
            reader.start()
            completed.add(done.await(2, TimeUnit.SECONDS))
            reader.join(TimeUnit.SECONDS.toMillis(2))
        }
        doAnswer { probe() }.whenever(store).setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
        repository.firstFlags.whenComplete { probe() }
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = probe()
            override fun onFailure(error: Throwable) = fail<Unit>("Unexpected failure", error)
        }
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(eq(context), eq(fakeDatadogContext), any())
        ).thenReturn("response")
        whenever(
            mockPrecomputeMapper.map(eq("response"), anyOrNull())
        ).thenReturn(PrecomputedAssignments(emptyMap(), null))

        // When
        manager.updateEvaluationsForContext(context, callback)

        // Then
        assertThat(completed).containsExactly(true, true, true)
    }

    @Test
    fun `M preserve state order W updateEvaluationsForContext() { newer context during storage submission }`() {
        // Given
        val first = EvaluationContext(fakeTargetingKey)
        val newer = EvaluationContext(fakeAttributeKey)
        val policy = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        val store = mock<DataStoreHandler>()
        val repository = DefaultFlagsRepository(mockSdkCore, fakeTargetingKey, store, persistenceLoadTimeoutMs = 0)
        val manager = EvaluationsManager(
            mockSdkCore, mockExecutorService, mockInternalLogger, repository,
            mockAssignmentsDownloader, mockPrecomputeMapper, mockFlagsStateManager,
            null, { _, _ -> {} }, policy
        )
        val savedContexts = mutableListOf<EvaluationContext>()
        doAnswer {
            val entry = it.getArgument<FlagsStateEntry>(1)
            savedContexts.add(entry.evaluationContext)
            if (entry.evaluationContext == first) manager.updateEvaluationsForContext(newer)
        }.whenever(store).setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext), any())
        ).thenReturn("response")
        whenever(
            mockPrecomputeMapper.map(eq("response"), anyOrNull())
        ).thenReturn(PrecomputedAssignments(emptyMap(), null))

        // When
        manager.updateEvaluationsForContext(first)

        // Then
        assertThat(savedContexts).containsExactly(first, newer)
        assertThat(repository.getEvaluationContext()).isEqualTo(newer)
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        }
    }

    @Test
    fun `M complete context without first flags W no op repository`() {
        // Given
        val repository = NoOpFlagsRepository()
        var notified = false
        repository.firstFlags.whenComplete { notified = true }
        val callback = mock<EvaluationContextCallback>()
        val context = EvaluationContext(fakeTargetingKey)
        val manager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = repository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} },
            experimentationOptOut = ExperimentationOptOut()
        )
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn("response")
        whenever(mockPrecomputeMapper.map("response")).thenReturn(PrecomputedAssignments(emptyMap(), null))

        // When
        manager.updateEvaluationsForContext(context, callback)

        // Then
        verify(callback).onSuccess()
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        assertThat(notified).isFalse()
        assertThat(repository.getAssignmentsSnapshot()?.flags.orEmpty()).isEmpty()
        assertThat(repository.getEvaluationContext()).isNull()
    }

    @Test
    fun `M process context successfully W updateEvaluationsForContext() { valid response }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, mapOf(fakeAttributeKey to fakeAttributeValue))
        val context = publicContext

        val mockResponse = """
        {
            "data": {
                "attributes": {
                    "flags": {
                        "test-flag": {
                            "variationType": "boolean",
                            "variationValue": true,
                            "doLog": true,
                            "allocationKey": "test-allocation",
                            "variationKey": "test-variation",
                            "extraLogging": {},
                            "reason": "test-reason"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val expectedFlags = mapOf(
            "test-flag" to PrecomputedFlag(
                variationType = "boolean",
                variationValue = "true",
                doLog = true,
                allocationKey = "test-allocation",
                variationKey = "test-variation",
                extraLogging = JSONObject(),
                reason = "test-reason"
            )
        )

        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(mockResponse)
        whenever(mockPrecomputeMapper.map(mockResponse)).thenReturn(PrecomputedAssignments(expectedFlags))

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).installFlagsAndContext(context, expectedFlags, null)
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M handle network failure gracefully W updateEvaluationsForContext() { network error }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        // When response is null, only 1 debug log (processing start) and 1 warn log (failure)
        verify(mockInternalLogger, times(1)).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M handle parsing failure gracefully W updateEvaluationsForContext() { invalid JSON }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext
        val callback = mock<EvaluationContextCallback>()

        val invalidResponse = "{ invalid json }"
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        ).thenReturn(invalidResponse)
        whenever(mockPrecomputeMapper.map(invalidResponse)).thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(context, callback)

        // Then
        verify(mockFlagsRepository, never()).installFlagsAndContext(any(), any(), anyOrNull())
        verify(mockFlagsStateManager).updateState(isA<FlagsClientState.Error>())
        verify(mockFlagsStateManager, never()).updateState(FlagsClientState.Ready)
        verify(callback).onFailure(any())
        verify(callback, never()).onSuccess()
    }

    @Test
    fun `M log processing start W updateEvaluationsForContext() { any context }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        val logCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger, times(1)).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            logCaptor.capture(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )

        assertThat(logCaptor.firstValue.invoke()).contains("Processing evaluation context: $fakeTargetingKey")
    }

    // region State Transitions

    @Test
    fun `M notify RECONCILING then READY W updateEvaluationsForContext() { successful fetch }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockResponse = "{}"
        val expectedFlags = mapOf<String, PrecomputedFlag>()

        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(publicContext),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(mockResponse)
        whenever(mockPrecomputeMapper.map(mockResponse)).thenReturn(PrecomputedAssignments(expectedFlags))

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        }
    }

    @Test
    fun `M notify RECONCILING then ERROR W updateEvaluationsForContext() { network failure, no previous flags }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())

        whenever(mockFlagsRepository.hasFlags()).thenReturn(false)

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(isA<FlagsClientState.Error>())
        }
    }

    @Test
    fun `M notify STALE W updateEvaluationsForContext() { network failure, cached flags, context matches }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())

        whenever(mockFlagsRepository.hasFlags()).thenReturn(true)
        whenever(mockFlagsRepository.getEvaluationContext()).thenReturn(publicContext)

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
        }
    }

    @Test
    fun `M notify ERROR W updateEvaluationsForContext() { network failure, cached flags, context mismatch }`() {
        // Given
        val requestedContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val cachedContext = EvaluationContext("different-user", emptyMap())

        whenever(mockFlagsRepository.hasFlags()).thenReturn(true)
        whenever(mockFlagsRepository.getEvaluationContext()).thenReturn(cachedContext)
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(requestedContext),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(requestedContext)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(isA<FlagsClientState.Error>())
        }
    }

    @Test
    fun `M invoke onSuccess W updateEvaluationsForContext() { success }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        val jsonResponse = EMPTY_FLAGS_RESPONSE_JSON
        val flagsMap = emptyMap<String, PrecomputedFlag>()

        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(publicContext),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(jsonResponse)
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(PrecomputedAssignments(flagsMap))

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext, callback = mockCallback)

        // Then
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
        verify(mockFlagsRepository).installFlagsAndContext(publicContext, emptyMap(), null)
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
    }

    @Test
    fun `M invoke onFailure W updateEvaluationsForContext() { network failure, no cached flags }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()

        whenever(mockFlagsRepository.hasFlags()).thenReturn(false)

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext, callback = mockCallback)

        // Then
        argumentCaptor<Throwable>().apply {
            verify(mockCallback).onFailure(capture())
            assertThat(firstValue.message).contains("Unable to fetch feature flags")
        }
    }

    @Test
    fun `M invoke onFailure W updateEvaluationsForContext() { network failure, has cached flags }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()

        whenever(mockFlagsRepository.hasFlags()).thenReturn(true)

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext, callback = mockCallback)

        // Then
        argumentCaptor<Throwable>().apply {
            verify(mockCallback).onFailure(capture())
            assertThat(firstValue.message).contains("Unable to fetch feature flags")
        }
    }

    @Test
    fun `M not invoke callback W updateEvaluationsForContext() { callback is null }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val jsonResponse = EMPTY_FLAGS_RESPONSE_JSON
        val flagsMap = emptyMap<String, PrecomputedFlag>()

        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(publicContext),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(jsonResponse)
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(PrecomputedAssignments(flagsMap))

        // When / Then - should not throw
        assertDoesNotThrow { evaluationsManager.updateEvaluationsForContext(publicContext, callback = null) }
    }

    @Test
    fun `M fail initialization callback W updateEvaluationsForContext() { timeout, operation completes late }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        var scheduledTimeoutMs: Long? = null
        var timeoutAction: (() -> Unit)? = null
        var operation: Runnable? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { timeoutMs, action ->
                scheduledTimeoutMs = timeoutMs
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)
        checkNotNull(timeoutAction).invoke()

        // Then
        assertThat(scheduledTimeoutMs).isEqualTo(2_500L)
        argumentCaptor<Throwable>().apply {
            verify(mockCallback).onFailure(capture())
            assertThat(firstValue).isInstanceOf(FlagsInitializationTimeoutException::class.java)
            assertThat(firstValue.message).isEqualTo("Flags initialization timed out after 2500ms")
            verify(mockFlagsStateManager).updateState(FlagsClientState.Error(firstValue))
        }
        verify(mockCallback, never()).onSuccess()

        // When
        checkNotNull(operation).run()

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        verify(mockCallback, never()).onSuccess()
        verify(mockCallback, times(1)).onFailure(any())
    }

    @Test
    fun `M notify STALE before timeout callback W updateEvaluationsForContext() { cached flags match context }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        var timeoutAction: (() -> Unit)? = null
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        var stateAtTimeoutCallback: FlagsClientState? = null
        var callbackError: Throwable? = null
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                fail<Unit>("onSuccess should not be called")
            }

            override fun onFailure(error: Throwable) {
                stateAtTimeoutCallback = stateManager.getCurrentState()
                callbackError = error
            }
        }
        whenever(mockFlagsRepository.hasFlags()).thenReturn(true)
        whenever(mockFlagsRepository.getEvaluationContext()).thenReturn(context)
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        ).thenAnswer {
            checkNotNull(timeoutAction).invoke()
            null
        }
        val testedManager = createManager(
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            scheduler = { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, callback)

        // Then
        assertThat(callbackError).isInstanceOf(FlagsInitializationTimeoutException::class.java)
        assertThat(stateAtTimeoutCallback).isEqualTo(FlagsClientState.Stale)
    }

    @Test
    fun `M notify STALE W updateEvaluationsForContext() { cache loads before timeout }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        var timeoutAction: (() -> Unit)? = null
        var stateAtTimeoutCallback: FlagsClientState? = null
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        whenever(mockFlagsRepository.hasFlags()).thenReturn(false)
        whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenReturn(true)
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        ).thenAnswer {
            checkNotNull(timeoutAction).invoke()
            null
        }
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                fail<Unit>("onSuccess should not be called")
            }

            override fun onFailure(error: Throwable) {
                stateAtTimeoutCallback = stateManager.getCurrentState()
            }
        }
        val testedManager = createManager(
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            scheduler = { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, callback)

        // Then
        assertThat(stateAtTimeoutCallback).isEqualTo(FlagsClientState.Stale)
    }

    @Test
    fun `M keep ready state W updateEvaluationsForContext() { newer request completed before timeout }`() {
        // Given
        val mockFirstCallback = mock<EvaluationContextCallback>()
        val operations = mutableListOf<Runnable>()
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operations += it.getArgument<Runnable>(0)
            null
        }
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                any(),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        val testedManager = createManager(
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            scheduler = { _, action ->
                timeoutAction = action
                {}
            }
        )

        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), mockFirstCallback)
        testedManager.updateEvaluationsForContext(EvaluationContext("newer", emptyMap()))

        // When
        operations[1].run()
        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        checkNotNull(timeoutAction).invoke()

        // Then
        verify(mockFirstCallback).onFailure(any<FlagsInitializationTimeoutException>())
        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        val diagnostics = argumentCaptor<Diagnostics>()
        verify(mockAssignmentsDownloader).readPrecomputedFlags(
            eq(EvaluationContext("newer", emptyMap())),
            eq(fakeDatadogContext),
            diagnostics.capture()
        )
        assertThat(diagnostics.firstValue).isEqualTo(
            Diagnostics(
                diagnostics.firstValue.sdk,
                Fetch(2, Trigger.CONTEXT_CHANGE)
            )
        )
    }

    @Test
    fun `M not wait for cache W updateEvaluationsForContext() { initialization timeout expires }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val cacheReadStarted = CountDownLatch(1)
        val releaseCacheRead = CountDownLatch(1)
        val callbackCompleted = CountDownLatch(1)
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        whenever(mockFlagsRepository.hasFlags()).thenAnswer {
            cacheReadStarted.countDown()
            check(releaseCacheRead.await(1, TimeUnit.SECONDS))
            false
        }
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                fail<Unit>("onSuccess should not be called")
            }

            override fun onFailure(error: Throwable) {
                callbackCompleted.countDown()
            }
        }
        val testedManager = createManager(
            initializationTimeoutMs = 0,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )
        testedManager.updateEvaluationsForContext(context, callback)
        val timeoutThread = Thread { checkNotNull(timeoutAction).invoke() }

        try {
            // When
            timeoutThread.start()

            // Then
            assertThat(callbackCompleted.await(250, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(cacheReadStarted.count).isEqualTo(1)
        } finally {
            releaseCacheRead.countDown()
            timeoutThread.join(TimeUnit.SECONDS.toMillis(2))
        }
    }

    @Test
    fun `M timeout first callback W updateEvaluationsForContext() { reconciling listener re-enters }`() {
        // Given
        val mockFirstCallback = mock<EvaluationContextCallback>()
        val mockNestedCallback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        lateinit var testedManager: EvaluationsManager
        val didReenter = AtomicBoolean(false)
        stateManager.addListener(
            object : FlagsStateListener {
                override fun onStateChanged(newState: FlagsClientState) {
                    if (newState == FlagsClientState.Reconciling && didReenter.compareAndSet(false, true)) {
                        testedManager.updateEvaluationsForContext(
                            EvaluationContext("nested", emptyMap()),
                            mockNestedCallback
                        )
                    }
                }
            }
        )
        testedManager = createManager(
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            scheduler = { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), mockFirstCallback)
        checkNotNull(timeoutAction).invoke()

        // Then
        verify(mockFirstCallback).onFailure(any<FlagsInitializationTimeoutException>())
        verify(mockNestedCallback, never()).onFailure(any())
    }

    @Test
    fun `M keep ready state W updateEvaluationsForContext() { success races timeout publication }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        var operation: Runnable? = null
        val currentState = AtomicReference<FlagsClientState>(FlagsClientState.NotReady)
        val timeoutReadState = CountDownLatch(1)
        val releaseTimeout = CountDownLatch(1)
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        doAnswer {
            val newState = it.getArgument<FlagsClientState>(0)
            if (newState is FlagsClientState.Error) {
                timeoutReadState.countDown()
                check(releaseTimeout.await(5, TimeUnit.SECONDS))
            }
            currentState.set(newState)
            null
        }.whenever(mockFlagsStateManager).updateState(any())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)
        val timeoutThread = Thread { checkNotNull(timeoutAction).invoke() }
        timeoutThread.start()
        check(timeoutReadState.await(5, TimeUnit.SECONDS))
        val operationThread = Thread { checkNotNull(operation).run() }
        operationThread.start()
        operationThread.join(TimeUnit.SECONDS.toMillis(2))
        releaseTimeout.countDown()
        timeoutThread.join(TimeUnit.SECONDS.toMillis(5))
        operationThread.join(TimeUnit.SECONDS.toMillis(5))

        // Then
        assertThat(timeoutThread.isAlive).isFalse()
        assertThat(operationThread.isAlive).isFalse()
        assertThat(currentState.get()).isEqualTo(FlagsClientState.Ready)
        verify(mockCallback).onFailure(any<FlagsInitializationTimeoutException>())
        verify(mockCallback, never()).onSuccess()
    }

    @Test
    fun `M cancel initialization timeout W updateEvaluationsForContext() { operation completes first }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        var cancellationCount = 0
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                { cancellationCount += 1 }
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)
        checkNotNull(timeoutAction).invoke()

        // Then
        assertThat(cancellationCount).isEqualTo(1)
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
    }

    @Test
    fun `M keep initialization timeout active W updateEvaluationsForContext() { decoding assignments }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenAnswer {
            checkNotNull(timeoutAction).invoke()
            PrecomputedAssignments(emptyMap())
        }
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)

        // Then
        verify(mockCallback).onFailure(any<FlagsInitializationTimeoutException>())
        verify(mockCallback, never()).onSuccess()
        verify(mockFlagsRepository).installFlagsAndContext(context, emptyMap(), null)
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
    }

    @Test
    fun `M schedule timeout once W updateEvaluationsForContext() { context changes after initialization }`() {
        // Given
        val mockCallback = mock<EvaluationContextCallback>()
        var scheduleCount = 0
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                any(),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, _ ->
                scheduleCount += 1
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), mockCallback)
        testedManager.updateEvaluationsForContext(EvaluationContext("second", emptyMap()), mockCallback)

        // Then
        assertThat(scheduleCount).isEqualTo(1)
        verify(mockCallback, times(2)).onSuccess()
    }

    @Test
    fun `M not schedule timeout W updateEvaluationsForContext() { initialization timeout is not configured }`() {
        // Given
        val mockCallback = mock<EvaluationContextCallback>()
        var scheduleCount = 0
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                any(),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val testedManager = createManager(
            initializationTimeoutMs = null,
            scheduler = InitializationTimeoutScheduler { _, _ ->
                scheduleCount += 1
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), mockCallback)

        // Then
        assertThat(scheduleCount).isZero()
        verify(mockCallback).onSuccess()
    }

    @Test
    fun `M update state to ERROR W updateEvaluationsForContext() { timeout, callback is null }`() {
        // Given
        var timeoutAction: (() -> Unit)? = null
        var operation: Runnable? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), callback = null)
        checkNotNull(timeoutAction).invoke()

        // Then
        argumentCaptor<FlagsClientState>().apply {
            verify(mockFlagsStateManager, times(2)).updateState(capture())
            val errorState = allValues.filterIsInstance<FlagsClientState.Error>().single()
            assertThat(errorState.error).isInstanceOf(FlagsInitializationTimeoutException::class.java)
        }
        assertThat(operation).isNotNull()
    }

    @Test
    fun `M claim success W updateEvaluationsForContext() { timeout fires from ready listener }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        stateManager.addListener(
            object : FlagsStateListener {
                override fun onStateChanged(newState: FlagsClientState) {
                    if (newState == FlagsClientState.Ready) timeoutAction?.invoke()
                }
            }
        )
        val testedManager = createManager(
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            scheduler = { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)

        // Then
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M keep ERROR state W updateEvaluationsForContext() { immediate initialization timeout }`() {
        // Given
        val mockCallback = mock<EvaluationContextCallback>()
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        val testedManager = createManager(
            flagStateManager = stateManager,
            initializationTimeoutMs = 0,
            scheduler = { _, action ->
                action()
                val cancelTimeout: () -> Unit = {}
                cancelTimeout
            }
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), mockCallback)

        // Then
        verify(mockCallback).onFailure(any<FlagsInitializationTimeoutException>())
        assertThat(stateManager.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
    }

    @Test
    fun `M not cancel timeout W updateEvaluationsForContext() { timeout expires }`() {
        // Given
        val mockCallback = mock<EvaluationContextCallback>()
        var cancellationCount = 0
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        val testedManager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                action()
                val cancelTimeout: () -> Unit = { cancellationCount += 1 }
                cancelTimeout
            }
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), mockCallback)

        // Then
        assertThat(cancellationCount).isZero()
        verify(mockCallback).onFailure(any<FlagsInitializationTimeoutException>())
    }

    @Test
    fun `M have state READY when callback invoked W updateEvaluationsForContext() { success }`() {
        // Given
        val realStateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )

        val evaluationsManagerWithRealState = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = realStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} },
            experimentationOptOut = ExperimentationOptOut()
        )

        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val jsonResponse = EMPTY_FLAGS_RESPONSE_JSON
        val flagsMap = emptyMap<String, PrecomputedFlag>()

        var stateWhenCallbackInvoked: FlagsClientState? = null

        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                stateWhenCallbackInvoked = realStateManager.getCurrentState()
            }

            override fun onFailure(error: Throwable) {
                fail<Unit>("onFailure should not be called in success case, but was called with: $error")
            }
        }

        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(publicContext),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(jsonResponse)
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(PrecomputedAssignments(flagsMap))

        // When
        evaluationsManagerWithRealState.updateEvaluationsForContext(publicContext, callback = callback)

        // Then
        assertThat(stateWhenCallbackInvoked).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M notify first flags once W successful response arrives after initialization timeout`() {
        // Given
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        val repository = DefaultFlagsRepository(mockSdkCore, "late-success", mock(), persistenceLoadTimeoutMs = 1)
        val state = FlagsStateManager(
            FlagsStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
        )
        val work = ArrayDeque<Runnable>()
        doAnswer {
            work.add(it.getArgument(0))
            null
        }.whenever(mockExecutorService).execute(any())
        lateinit var timeout: () -> Unit
        val manager = EvaluationsManager(
            mockSdkCore,
            mockExecutorService,
            mockInternalLogger,
            repository,
            mockAssignmentsDownloader,
            mockPrecomputeMapper,
            state,
            1000,
            initializationTimeoutScheduler = { _, action ->
                timeout = action;
                {}
            },
            experimentationOptOut = ExperimentationOptOut()
        )
        val context = EvaluationContext(fakeTargetingKey)
        val callback = mock<EvaluationContextCallback>()
        val firstKeys = mutableListOf<List<String>>()
        repository.firstFlags.whenComplete { firstKeys.add(it) }
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn("response")
        whenever(mockPrecomputeMapper.map("response"))
            .thenReturn(PrecomputedAssignments(mapOf("enabled" to mock())))

        // When
        manager.updateEvaluationsForContext(context, callback)
        timeout()

        // Then
        assertThat(firstKeys).isEmpty()
        assertThat(state.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        verify(callback).onFailure(any<FlagsInitializationTimeoutException>())
        verify(callback, never()).onSuccess()

        // When
        work.removeFirst().run()

        // Then
        assertThat(firstKeys).containsExactly(listOf("enabled"))
        assertThat(repository.getAssignmentsSnapshot()?.flags.orEmpty()).containsOnlyKeys("enabled")
        assertThat(state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun `M disarm old timeout before first flags W storage throws and callback changes context`() {
        // Given
        val store = mock<DataStoreHandler>()
        val storageError = IllegalStateException("storage unavailable")
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSdkCore.timeProvider).thenReturn(mock())
        doThrow(storageError).whenever(store)
            .setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
        val repository = DefaultFlagsRepository(mockSdkCore, "throwing", store, persistenceLoadTimeoutMs = 1)
        val state = FlagsStateManager(
            FlagsStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
        )
        val work = ArrayDeque<Runnable>()
        doAnswer {
            work.add(it.getArgument(0))
            null
        }.whenever(mockExecutorService).execute(any())
        lateinit var timeout: () -> Unit
        var cancelled = false
        val manager = EvaluationsManager(
            mockSdkCore,
            mockExecutorService,
            mockInternalLogger,
            repository,
            mockAssignmentsDownloader,
            mockPrecomputeMapper,
            state,
            1000,
            initializationTimeoutScheduler = { _, action ->
                timeout = action;
                { cancelled = true }
            },
            experimentationOptOut = ExperimentationOptOut()
        )
        val context = EvaluationContext(fakeTargetingKey)
        val callback = mock<EvaluationContextCallback>()
        var firstFlagsCount = 0
        repository.firstFlags.whenComplete {
            firstFlagsCount++
            manager.updateEvaluationsForContext(EvaluationContext("newer"))
        }
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn("response")
        whenever(mockPrecomputeMapper.map("response")).thenReturn(PrecomputedAssignments(emptyMap()))

        // When
        manager.updateEvaluationsForContext(context, callback)
        work.removeFirst().run()
        timeout()

        // Then
        assertThat(state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        assertThat(cancelled).isTrue()
        assertThat(firstFlagsCount).isEqualTo(1)
        assertThat(work).hasSize(1)
        verify(callback).onSuccess()
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun `M recheck cache W updateEvaluationsForContext() {cache loads during invalidation}`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey)
        whenever(mockFlagsRepository.clearFlagsIfContextChanged(context)).thenAnswer {
            whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenReturn(true)
            null
        }

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
        verify(mockFlagsStateManager, never()).updateState(isA<FlagsClientState.Error>())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve newer reconciliation W updateEvaluationsForContext() {cache delivery delayed}`(
        sameContext: Boolean
    ) {
        // Given
        val state = createStateManager()
        val manager = createManager(state, scheduler = { _, _ -> {} })
        val context = EvaluationContext(fakeTargetingKey)
        manager.updateEvaluationsForContext(context)
        whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenReturn(true)
        var delivery: Runnable? = null
        mockFlagsRepository.cachedFlagsLoaded.complete(listOf(fakeAttributeKey)) { delivery = it }
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        val nextContext = if (sameContext) context else context.copy(targetingKey = fakeAttributeKey)

        // When
        manager.updateEvaluationsForContext(nextContext)
        checkNotNull(delivery).run()

        // Then
        assertThat(state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
    }

    @Test
    fun `M preserve newer reconciliation W updateEvaluationsForContext() {reentrant cache delivery}`() {
        // Given
        val state = createStateManager()
        val manager = createManager(state, scheduler = { _, _ -> {} })
        val context = EvaluationContext(fakeTargetingKey)
        val pendingWork = mutableListOf<Runnable>()
        whenever(mockExecutorService.execute(any())).thenAnswer {
            pendingWork.add(it.getArgument(0))
            null
        }
        state.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState is FlagsClientState.Error) {
                    manager.updateEvaluationsForContext(context)
                    whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenReturn(true)
                    mockFlagsRepository.cachedFlagsLoaded.complete(listOf(fakeAttributeKey))
                }
            }
        })

        // When
        manager.updateEvaluationsForContext(context)
        pendingWork.removeAt(0).run()

        // Then
        assertThat(state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
        assertThat(pendingWork).hasSize(1)
    }

    @Test
    fun `M preserve ready state W updateEvaluationsForContext() {cache delivery follows network success}`() {
        // Given
        val state = createStateManager()
        val manager = createManager(state, scheduler = { _, _ -> {} })
        val context = EvaluationContext(fakeTargetingKey)
        manager.updateEvaluationsForContext(context)
        whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenReturn(true)
        var delivery: Runnable? = null
        mockFlagsRepository.cachedFlagsLoaded.complete(listOf(fakeAttributeKey)) { delivery = it }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(eq(context), eq(fakeDatadogContext), any()))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))

        // When
        manager.updateEvaluationsForContext(context)
        checkNotNull(delivery).run()

        // Then
        assertThat(state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M recover only usable cache W complete() {initialization timed out}`(usableCache: Boolean) {
        // Given
        val state = createStateManager()
        var timeout: (() -> Unit)? = null
        val manager = createManager(state, 1_000) { _, action ->
            timeout = action
            {}
        }
        val context = EvaluationContext(fakeTargetingKey)
        val callback = mock<EvaluationContextCallback>()
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        manager.updateEvaluationsForContext(context, callback)
        checkNotNull(timeout).invoke()
        val timedOutState = state.getCurrentState()
        whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenReturn(usableCache)

        // When
        mockFlagsRepository.cachedFlagsLoaded.complete(if (usableCache) listOf(fakeAttributeKey) else emptyList())

        // Then
        assertThat(timedOutState).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(state.getCurrentState()).isEqualTo(if (usableCache) FlagsClientState.Stale else timedOutState)
        verify(callback).onFailure(isA<FlagsInitializationTimeoutException>())
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun `M preserve newer reconciliation W complete() {request starts during cache recovery check}`() {
        // Given
        val state = createStateManager()
        val manager = createManager(state, scheduler = { _, _ -> {} })
        val context = EvaluationContext(fakeTargetingKey)
        manager.updateEvaluationsForContext(context)
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        whenever(mockFlagsRepository.hasLoadedFlagsForContext(context)).thenAnswer {
            manager.updateEvaluationsForContext(context)
            true
        }

        // When
        mockFlagsRepository.cachedFlagsLoaded.complete(listOf(fakeAttributeKey))

        // Then
        assertThat(state.getCurrentState()).isEqualTo(FlagsClientState.Reconciling)
    }

    private fun createStateManager(): FlagsStateManager = FlagsStateManager(
        FlagsStateHolder.create(
            initialState = FlagsClientState.NotReady,
            onStateChanged = FlagsStateListener::onStateChanged
        )
    )

    // region Cold-start integration

    @Test
    fun `M retain matching assignments on invalid encoding W updateEvaluationsForContext() { invalid refresh }`(
        @Mock mockCallback: EvaluationContextCallback
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(source = "android")
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val testedRepository = createRepository()
        val testedManager = createManagerWithRepository(testedRepository)
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(ENCODED_RESPONSE_JSON)

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)

        // Then
        assertThat(checkNotNull(testedRepository.getPrecomputedFlag("flag")).variationValue).isEqualTo("true")
        verify(mockCallback).onSuccess()

        // Given
        val invalidResponse = ENCODED_RESPONSE_JSON.replace("flag-key-sha256-v1", "unknown")
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        ).thenReturn(invalidResponse)

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
        val failure = argumentCaptor<NetworkRequestFailedException>()
        verify(mockCallback).onFailure(failure.capture())
        assertThat(failure.firstValue.message).isEqualTo("Unable to read the feature flags response.")
        assertThat(checkNotNull(testedRepository.getPrecomputedFlag("flag")).variationValue).isEqualTo("true")

        // Given
        val otherContext = EvaluationContext("$fakeTargetingKey-other", emptyMap())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(otherContext),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(invalidResponse)

        // When
        testedManager.updateEvaluationsForContext(otherContext, mockCallback)

        // Then
        val states = argumentCaptor<FlagsClientState>()
        verify(mockFlagsStateManager, times(6)).updateState(states.capture())
        assertThat(states.lastValue).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(testedRepository.getEvaluationContext()).isNull()
        assertThat(testedRepository.getPrecomputedFlag("flag")).isNull()
    }

    @Test
    fun `M reject unsolicited encoding W updateEvaluationsForContext() { React Native source }`(
        @Mock mockCallback: EvaluationContextCallback
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(source = "react-native")
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val testedRepository = createRepository()
        val testedManager = createManagerWithRepository(testedRepository)
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(ENCODED_RESPONSE_JSON)

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)

        // Then
        val failure = argumentCaptor<NetworkRequestFailedException>()
        verify(mockCallback).onFailure(failure.capture())
        assertThat(failure.firstValue.message).isEqualTo("Unable to read the feature flags response.")
        assertThat(testedRepository.hasFlags()).isFalse()
        assertThat(testedRepository.getAssignmentsSnapshot()?.flags.orEmpty()).isEmpty()
    }

    @Test
    fun `M notify STALE W updateEvaluationsForContext() { cold start network failure, cached flags match context }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val flag = PrecomputedFlag(
            variationType = "boolean",
            variationValue = "true",
            doLog = false,
            allocationKey = "alloc",
            variationKey = "var",
            extraLogging = JSONObject(),
            reason = "DEFAULT"
        )
        val persistedEntry = FlagsStateEntry(
            evaluationContext = context,
            flags = mapOf("cached-flag" to flag),
            lastUpdateTimestamp = 0L
        )

        // Configure DataStore to fire callback synchronously during DefaultFlagsRepository
        // construction, so the persistence latch is counted down before hasFlags() is called.
        val mockDataStore = mock<DataStoreHandler>()
        whenever(
            mockDataStore.value<FlagsStateEntry>(
                key = any(),
                version = anyOrNull(),
                callback = any(),
                deserializer = any()
            )
        ).doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2)
                .onSuccess(DataStoreContent(versionCode = 0, data = persistedEntry))
            null
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockSdkCore.timeProvider) doReturn mock()

        val realRepository = DefaultFlagsRepository(
            featureSdkCore = mockSdkCore,
            dataStore = mockDataStore,
            instanceName = "integration-test"
        )

        // Network call returns null (simulates network failure)

        val integrationManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = realRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} },
            experimentationOptOut = ExperimentationOptOut()
        )

        // When
        integrationManager.updateEvaluationsForContext(context)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
        }
    }

    // endregion

    @ParameterizedTest
    @CsvSource("IMMEDIATE,true", "IMMEDIATE,false", "FUTURE_EXPERIMENTS_ONLY,true", "FUTURE_EXPERIMENTS_ONLY,false")
    fun `M fail superseded callback W updateEvaluationsForContext() {superseded fetch fails}`(
        mode: ExperimentationOptOutMode,
        networkFailed: Boolean,
        forge: Forge
    ) {
        // Given
        val tasks = mutableListOf<Runnable>()
        val testedManager = queuedManager(mode, tasks)
        val first = EvaluationContext(forge.anAlphabeticalString())
        val firstCallback = mock<EvaluationContextCallback>()
        val newerCallback = mock<EvaluationContextCallback>()
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(eq(first), any(), any())) doReturn
            if (networkFailed) null else "unreadable"
        testedManager.updateEvaluationsForContext(first, firstCallback)
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()), newerCallback)

        // When
        tasks[0].run()

        // Then
        verify(firstCallback).onFailure(isA<FlagsContextSupersededException>())
        verify(mockFlagsStateManager, never()).updateState(isA<FlagsClientState.Error>())
        verify(mockFlagsStateManager, never()).updateState(FlagsClientState.Stale)
        verifyNoInteractions(newerCallback)
    }

    @Test
    fun `M preserve newer state W updateEvaluationsForContext() {disabled, earlier fetch fails}`(forge: Forge) {
        // Given
        val tasks = mutableListOf<Runnable>()
        val testedManager = queuedManager(ExperimentationOptOutMode.DISABLED, tasks)
        val firstCallback = mock<EvaluationContextCallback>()
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()), firstCallback)
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()))

        // When
        tasks[0].run()

        // Then
        verify(firstCallback).onFailure(isA<NetworkRequestFailedException>())
        verify(mockFlagsStateManager, never()).updateState(isA<FlagsClientState.Error>())
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M keep initialization timeout W updateEvaluationsForContext() {invalid timestamp first}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val scheduled = mutableListOf<Long>()
        val timeouts = mutableListOf<() -> Unit>()
        val testedManager = queuedManager(mode, mutableListOf(), timeouts, scheduled)
        val invalidCallback = mock<EvaluationContextCallback>()
        val validCallback = mock<EvaluationContextCallback>()
        testedManager.updateEvaluationsForContext(
            EvaluationContext(forge.anAlphabeticalString(), mapOf(ExperimentationOptOut.ATTRIBUTE to "junk")),
            invalidCallback
        )

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()), validCallback)
        timeouts.single().invoke()

        // Then
        assertThat(scheduled).containsExactly(QUEUED_TIMEOUT_MS)
        verify(invalidCallback).onFailure(isA<FlagsInvalidContextException>())
        verify(validCallback).onFailure(isA<FlagsInitializationTimeoutException>())
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M cancel initialization timeout W updateEvaluationsForContext() {first completes, newer loading}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val tasks = mutableListOf<Runnable>()
        val timeouts = mutableListOf<() -> Unit>()
        var cancellations = 0
        val testedManager = queuedManager(mode, tasks, timeouts, onCancel = { cancellations++ })
        val firstCallback = mock<EvaluationContextCallback>()
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), any(), any())) doReturn "response"
        whenever(mockPrecomputeMapper.map(any(), anyOrNull())) doReturn PrecomputedAssignments(emptyMap(), null)
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()), firstCallback)
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()))

        // When
        tasks[0].run()

        // Then
        assertThat(cancellations).isEqualTo(1)
        if (mode == ExperimentationOptOutMode.DISABLED) {
            verify(firstCallback).onSuccess()
        } else {
            verify(firstCallback).onFailure(isA<FlagsContextSupersededException>())
        }
    }

    @ParameterizedTest
    @CsvSource(
        "DISABLED,true",
        "DISABLED,false",
        "IMMEDIATE,true",
        "IMMEDIATE,false",
        "FUTURE_EXPERIMENTS_ONLY,true",
        "FUTURE_EXPERIMENTS_ONLY,false"
    )
    fun `M not complete twice W updateEvaluationsForContext() {fetch ends after timeout}`(
        mode: ExperimentationOptOutMode,
        succeeds: Boolean,
        forge: Forge
    ) {
        // Given
        val tasks = mutableListOf<Runnable>()
        val timeouts = mutableListOf<() -> Unit>()
        val testedManager = queuedManager(mode, tasks, timeouts)
        val callback = mock<EvaluationContextCallback>()
        if (succeeds) {
            whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), any(), any())) doReturn "response"
            whenever(mockPrecomputeMapper.map(any(), anyOrNull())) doReturn PrecomputedAssignments(emptyMap(), null)
        }
        testedManager.updateEvaluationsForContext(EvaluationContext(forge.anAlphabeticalString()), callback)
        timeouts.single().invoke()

        // When
        tasks.single().run()

        // Then
        verify(callback).onFailure(isA<FlagsInitializationTimeoutException>())
        verifyNoMoreInteractions(callback)
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M retain newer failure W updateEvaluationsForContext() {reentrant listener, FIFO fetches}`(
        mode: ExperimentationOptOutMode,
        forge: Forge
    ) {
        // Given
        val tasks = mutableListOf<Runnable>()
        val timeouts = mutableListOf<() -> Unit>()
        val fakeFirst = EvaluationContext(forge.anAlphabeticalString())
        val fakeNewer = EvaluationContext(forge.anAlphabeticalString())
        val firstCallback = mock<EvaluationContextCallback>()
        val stateManager = FlagsStateManager(
            FlagsStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        val testedManager = queuedManager(mode, tasks, timeouts, stateManager = stateManager)
        val states = mutableListOf<FlagsClientState>()
        var reentered = false
        stateManager.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                states += newState
                if (newState == FlagsClientState.Reconciling && !reentered) {
                    reentered = true
                    testedManager.updateEvaluationsForContext(fakeNewer)
                }
            }
        })

        // When
        testedManager.updateEvaluationsForContext(fakeFirst, firstCallback)
        tasks.removeAt(0).run()
        timeouts.single().invoke()

        // Then
        verify(mockAssignmentsDownloader).readPrecomputedFlags(eq(fakeNewer), any(), any())
        assertThat(tasks).hasSize(1)
        val errors = states.filterIsInstance<FlagsClientState.Error>()
        assertThat(stateManager.getCurrentState()).isSameAs(errors.last())
        if (mode == ExperimentationOptOutMode.DISABLED) {
            assertThat(errors).hasSize(2)
            assertThat(errors.last().error).isInstanceOf(FlagsInitializationTimeoutException::class.java)
        } else {
            assertThat(errors).hasSize(1)
            assertThat(errors.single().error).isInstanceOf(NetworkRequestFailedException::class.java)
        }
        verify(firstCallback).onFailure(isA<FlagsInitializationTimeoutException>())
    }

    @Test
    fun `M retain newer assignments W updateEvaluationsForContext() {older failure after FIFO reentry}`(forge: Forge) {
        // Given
        val tasks = ArrayDeque<Runnable>()
        doAnswer { tasks.addLast(it.getArgument(0)); null }.whenever(mockExecutorService).execute(any())
        val repository = createRepository()
        val states = createStateManager()
        val first = EvaluationContext(fakeTargetingKey)
        val newer = EvaluationContext(fakeAttributeKey)
        val assignments = forge.getForgery<PrecomputedAssignments>().copy(obfuscation = null)
        val callback = mock<EvaluationContextCallback>()
        val manager = EvaluationsManager(
            mockSdkCore, mockExecutorService, mockInternalLogger, repository,
            mockAssignmentsDownloader, mockPrecomputeMapper, states, null, { _, _ -> {} }, ExperimentationOptOut()
        )
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(eq(newer), any(), any())).thenReturn("newer")
        whenever(mockPrecomputeMapper.map("newer")).thenReturn(assignments)
        var reentered = false
        states.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState == FlagsClientState.Reconciling && !reentered) {
                    reentered = true
                    manager.updateEvaluationsForContext(newer)
                }
            }
        })

        // When
        manager.updateEvaluationsForContext(first, callback)
        tasks.removeFirst().run()
        assertThat(repository.getEvaluationContext()).isEqualTo(newer)
        tasks.removeFirst().run()

        // Then
        assertThat(repository.getEvaluationContext()).isEqualTo(newer)
        assertThat(checkNotNull(repository.getAssignmentsSnapshot()).flags).isEqualTo(assignments.flags)
        assertThat(states.getCurrentState()).isEqualTo(FlagsClientState.Ready)
        verify(callback).onFailure(isA<NetworkRequestFailedException>())
        verifyNoMoreInteractions(callback)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M recover empty cache W persistence completes after failure or timeout`(timedOut: Boolean, forge: Forge) {
        // Given
        val store = mock<DataStoreHandler>()
        lateinit var read: DataStoreReadCallback<FlagsStateEntry>
        doAnswer { read = it.getArgument(2); null }
            .whenever(store).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        val repository = DefaultFlagsRepository(mockSdkCore, fakeTargetingKey, store, persistenceLoadTimeoutMs = 0)
        val states = createStateManager()
        val tasks = ArrayDeque<Runnable>()
        doAnswer { tasks.addLast(it.getArgument(0)); null }.whenever(mockExecutorService).execute(any())
        lateinit var timeout: () -> Unit
        val manager = EvaluationsManager(
            mockSdkCore, mockExecutorService, mockInternalLogger, repository,
            mockAssignmentsDownloader, mockPrecomputeMapper, states, 1_000,
            { _, action -> timeout = action; {} }, ExperimentationOptOut()
        )
        val context = EvaluationContext(fakeTargetingKey)
        val callback = mock<EvaluationContextCallback>()
        val firstFlags = mutableListOf<List<String>>()
        repository.firstFlags.whenComplete { firstFlags.add(it) }
        manager.updateEvaluationsForContext(context, callback)
        if (timedOut) timeout() else tasks.removeFirst().run()
        assertThat(states.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)

        // When
        read.onSuccess(DataStoreContent(forge.anInt(), FlagsStateEntry(context, emptyMap(), forge.aLong())))

        // Then
        assertThat(states.getCurrentState()).isEqualTo(FlagsClientState.Stale)
        assertThat(repository.getEvaluationContext()).isEqualTo(context)
        assertThat(firstFlags).containsExactly(emptyList())
        verify(callback).onFailure(any())
        verifyNoMoreInteractions(callback)
    }

    private fun queuedManager(
        mode: ExperimentationOptOutMode,
        tasks: MutableList<Runnable>,
        timeouts: MutableList<() -> Unit>? = null,
        scheduled: MutableList<Long> = mutableListOf(),
        onCancel: () -> Unit = {},
        stateManager: FlagsStateManager = mockFlagsStateManager
    ): EvaluationsManager {
        doAnswer {
            tasks += it.getArgument<Runnable>(0)
            null
        }.whenever(mockExecutorService).execute(any())
        return EvaluationsManager(
            mockSdkCore,
            mockExecutorService,
            mockInternalLogger,
            mockFlagsRepository,
            mockAssignmentsDownloader,
            mockPrecomputeMapper,
            stateManager,
            if (timeouts == null) null else QUEUED_TIMEOUT_MS,
            { timeoutMs, action ->
                scheduled += timeoutMs
                timeouts?.add(action)
                onCancel
            },
            ExperimentationOptOut(mode)
        )
    }
    private fun createRepository(): DefaultFlagsRepository {
        val mockDataStore = mock<DataStoreHandler>()
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onFailure()
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        return DefaultFlagsRepository(mockSdkCore, fakeTargetingKey, mockDataStore)
    }

    private fun createManagerWithRepository(repository: FlagsRepository): EvaluationsManager = EvaluationsManager(
        sdkCore = mockSdkCore,
        executorService = mockExecutorService,
        internalLogger = mockInternalLogger,
        flagsRepository = repository,
        assignmentsReader = mockAssignmentsDownloader,
        precomputeMapper = PrecomputeMapper(mockInternalLogger),
        flagStateManager = mockFlagsStateManager,
        initializationTimeoutMs = null,
        initializationTimeoutScheduler = { _, _ -> {} },
        experimentationOptOut = ExperimentationOptOut()
    )

    private fun createManager(
        flagStateManager: FlagsStateManager = mockFlagsStateManager,
        initializationTimeoutMs: Long? = null,
        scheduler: InitializationTimeoutScheduler
    ): EvaluationsManager = EvaluationsManager(
        sdkCore = mockSdkCore,
        executorService = mockExecutorService,
        internalLogger = mockInternalLogger,
        flagsRepository = mockFlagsRepository,
        assignmentsReader = mockAssignmentsDownloader,
        precomputeMapper = mockPrecomputeMapper,
        flagStateManager = flagStateManager,
        initializationTimeoutMs = initializationTimeoutMs,
        initializationTimeoutScheduler = scheduler,
        experimentationOptOut = ExperimentationOptOut()
    )

    companion object {
        private const val QUEUED_TIMEOUT_MS = 1_000L
        private val ENCODED_RESPONSE_JSON = """
            {"data":{"attributes":{
              "obfuscated":true,
              "obfuscation":{"scheme":"flag-key-sha256-v1","salt":"000102030405060708090a0b0c0d0e0f"},
              "flags":{"9817872c144b018abd77e3915bd77e2c27f4f534dccff8ffaca27361e3a5e1ee":{
                "variationType":"boolean","variationValue":true,"doLog":true,
                "allocationKey":"allocation-123","variationKey":"variation-456",
                "reason":"TARGETING_MATCH","serialId":123,"extraLogging":{}
              }}
            }}}
        """.trimIndent()
        private const val EMPTY_FLAGS_RESPONSE_JSON = "{\"data\": {\"attributes\": {\"flags\": {}}}}"
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M fail superseded callback W updateEvaluationsForContext() {newer request installs}`(
        mode: ExperimentationOptOutMode
    ) {
        // Given
        val first = EvaluationContext("first-subject")
        val newer = EvaluationContext("newer-subject")
        val firstCallback = mock<EvaluationContextCallback>()
        val newerCallback = mock<EvaluationContextCallback>()
        lateinit var testedManager: EvaluationsManager
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(eq(first), any(), any())).thenAnswer {
            testedManager.updateEvaluationsForContext(newer, newerCallback)
            "first-response"
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(eq(newer), any(), any())) doReturn "newer-response"
        whenever(mockPrecomputeMapper.map(any(), anyOrNull())) doReturn PrecomputedAssignments(emptyMap(), null)
        testedManager = EvaluationsManager(
            mockSdkCore, mockExecutorService, mockInternalLogger, mockFlagsRepository,
            mockAssignmentsDownloader, mockPrecomputeMapper, mockFlagsStateManager,
            null, { _, _ -> {} }, ExperimentationOptOut(mode)
        )

        // When
        testedManager.updateEvaluationsForContext(first, firstCallback)

        // Then
        verify(newerCallback).onSuccess()
        verify(firstCallback).onFailure(isA<FlagsContextSupersededException>())
        verify(firstCallback, never()).onSuccess()
        verify(mockFlagsRepository, never()).installFlagsAndContext(eq(first), any(), anyOrNull())
    }

    @ParameterizedTest
    @CsvSource("IMMEDIATE,false", "IMMEDIATE,true", "FUTURE_EXPERIMENTS_ONLY,false", "FUTURE_EXPERIMENTS_ONLY,true")
    fun `M time out newer request W updateEvaluationsForContext() {first request superseded}`(
        mode: ExperimentationOptOutMode,
        cached: Boolean
    ) {
        // Given
        doAnswer { null }.whenever(mockExecutorService).execute(any())
        val fakeNewer = EvaluationContext("newer-subject")
        whenever(mockFlagsRepository.hasLoadedFlagsForContext(fakeNewer)) doReturn cached
        lateinit var timeout: () -> Unit
        val manager = EvaluationsManager(
            mockSdkCore,
            mockExecutorService,
            mockInternalLogger,
            mockFlagsRepository,
            mockAssignmentsDownloader,
            mockPrecomputeMapper,
            mockFlagsStateManager,
            1000,
            initializationTimeoutScheduler = { _, action ->
                timeout = action;
                {}
            },
            experimentationOptOut = ExperimentationOptOut(mode)
        )
        val firstCallback = mock<EvaluationContextCallback>()
        manager.updateEvaluationsForContext(EvaluationContext("first-subject"), firstCallback)
        manager.updateEvaluationsForContext(fakeNewer)

        // When
        timeout()

        // Then
        verify(firstCallback).onFailure(isA<FlagsInitializationTimeoutException>())
        if (cached) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
        } else {
            verify(mockFlagsStateManager).updateState(isA<FlagsClientState.Error>())
        }
        verify(mockFlagsRepository).hasLoadedFlagsForContext(fakeNewer)
    }
}
