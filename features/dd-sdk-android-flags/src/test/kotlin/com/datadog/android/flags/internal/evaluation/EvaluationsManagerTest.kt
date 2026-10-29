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
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsStateListener
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
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsResponse
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.utils.DDCoreStateHolder
import com.datadog.android.utils.verifyLog
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
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
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
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationsManagerTest {

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
        evaluationsManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} }
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockFlagsFeatureScope
        whenever(mockSdkCore.source) doAnswer { fakeDatadogContext.source }
        whenever(mockSdkCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doAnswer {
            fakeDatadogContext
        }

        doAnswer {
            it.getArgument<() -> Unit>(3).invoke()
            null
        }.whenever(mockFlagsRepository).setFlagsAndContext(any(), any(), anyOrNull(), any())

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
            verify(mockFlagsRepository).hasConfiguration()
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
            initializationTimeoutScheduler = { _, _ -> {} }
        )
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(PrecomputedAssignmentsResponse("response", null))
        whenever(mockPrecomputeMapper.map("response")).thenReturn(PrecomputedAssignments(emptyMap(), null))

        // When
        manager.updateEvaluationsForContext(context, callback)

        // Then
        verify(callback).onSuccess()
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        assertThat(notified).isFalse()
        assertThat(repository.getFlagsSnapshot()).isEmpty()
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
            .thenReturn(PrecomputedAssignmentsResponse(mockResponse, null))
        whenever(mockPrecomputeMapper.map(mockResponse)).thenReturn(PrecomputedAssignments(expectedFlags))

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(eq(context), eq(expectedFlags), anyOrNull(), any())
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
        ).thenReturn(PrecomputedAssignmentsResponse(invalidResponse, null))
        whenever(mockPrecomputeMapper.map(invalidResponse)).thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(context, callback)

        // Then
        verify(mockFlagsRepository, never()).setFlagsAndContext(any(), any(), anyOrNull(), any())
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
            .thenReturn(PrecomputedAssignmentsResponse(mockResponse, null))
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

        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(false)

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

        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(true)
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

        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(true)
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
            .thenReturn(PrecomputedAssignmentsResponse(jsonResponse, null))
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(PrecomputedAssignments(flagsMap))

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext, callback = mockCallback)

        // Then
        verify(mockCallback).onSuccess()
        verify(mockCallback, never()).onFailure(any())
        verify(mockFlagsRepository).setFlagsAndContext(eq(publicContext), eq(emptyMap()), anyOrNull(), any())
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
    }

    @Test
    fun `M invoke onFailure W updateEvaluationsForContext() { network failure, no cached flags }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()

        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(false)

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

        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(true)

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
            .thenReturn(PrecomputedAssignmentsResponse(jsonResponse, null))
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(PrecomputedAssignments(flagsMap))

        // When/Then - should not throw
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
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
            DDCoreStateHolder.create(
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
        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(true)
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
            DDCoreStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        whenever(mockFlagsRepository.hasConfiguration()).thenReturn(false)
        whenever(mockFlagsRepository.hasConfigurationForContext(context)).thenReturn(true)
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(
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
        whenever(mockFlagsRepository.hasConfiguration()).thenAnswer {
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
            DDCoreStateHolder.create(
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
        val readyPublished = CountDownLatch(1)
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        whenever(mockFlagsStateManager.getCurrentState()).thenAnswer {
            val observedState = currentState.get()
            timeoutReadState.countDown()
            check(releaseTimeout.await(5, TimeUnit.SECONDS))
            observedState
        }
        doAnswer {
            val newState = it.getArgument<FlagsClientState>(0)
            currentState.set(newState)
            if (newState == FlagsClientState.Ready) readyPublished.countDown()
            null
        }.whenever(mockFlagsStateManager).updateState(any())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE)
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
        val waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (
            readyPublished.count > 0 &&
            operationThread.state != Thread.State.BLOCKED &&
            System.nanoTime() < waitDeadline
        ) {
            Thread.yield()
        }
        releaseTimeout.countDown()
        timeoutThread.join(TimeUnit.SECONDS.toMillis(5))
        operationThread.join(TimeUnit.SECONDS.toMillis(5))

        // Then
        assertThat(timeoutThread.isAlive).isFalse()
        assertThat(operationThread.isAlive).isFalse()
        assertThat(currentState.get()).isEqualTo(FlagsClientState.Ready)
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
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
        verify(mockFlagsRepository).setFlagsAndContext(eq(context), eq(emptyMap()), anyOrNull(), any())
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
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
            .thenReturn(EMPTY_FLAGS_RESPONSE)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(
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
            DDCoreStateHolder.create(
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
            DDCoreStateHolder.create(
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
            initializationTimeoutScheduler = { _, _ -> {} }
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
            .thenReturn(PrecomputedAssignmentsResponse(jsonResponse, null))
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
        val repository = DefaultFlagsRepository(mockSdkCore, "late-success", mock(), null, persistenceLoadTimeoutMs = 1)
        val state = FlagsStateManager(
            DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
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
            1000
        ) { _, action ->
            timeout = action;
            {}
        }
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
            .thenReturn(PrecomputedAssignmentsResponse("response", null))
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
        assertThat(repository.getFlagsSnapshot()).containsOnlyKeys("enabled")
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
        val repository = DefaultFlagsRepository(mockSdkCore, "throwing", store, null, persistenceLoadTimeoutMs = 1)
        val state = FlagsStateManager(
            DDCoreStateHolder.create(FlagsClientState.NotReady, FlagsStateListener::onStateChanged)
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
            1000
        ) { _, action ->
            timeout = action;
            { cancelled = true }
        }
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
            .thenReturn(PrecomputedAssignmentsResponse("response", null))
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
            .thenReturn(PrecomputedAssignmentsResponse(ENCODED_RESPONSE_JSON, null))

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
        ).thenReturn(PrecomputedAssignmentsResponse(invalidResponse, null))

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
            .thenReturn(PrecomputedAssignmentsResponse(invalidResponse, null))

        // When
        testedManager.updateEvaluationsForContext(otherContext, mockCallback)

        // Then
        val states = argumentCaptor<FlagsClientState>()
        verify(mockFlagsStateManager, times(6)).updateState(states.capture())
        assertThat(states.lastValue).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(context)
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
            .thenReturn(PrecomputedAssignmentsResponse(ENCODED_RESPONSE_JSON, null))

        // When
        testedManager.updateEvaluationsForContext(context, mockCallback)

        // Then
        val failure = argumentCaptor<NetworkRequestFailedException>()
        verify(mockCallback).onFailure(failure.capture())
        assertThat(failure.firstValue.message).isEqualTo("Unable to read the feature flags response.")
        assertThat(testedRepository.hasConfiguration()).isFalse()
        assertThat(testedRepository.getFlagsSnapshot()).isEmpty()
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
            lastUpdateTimestamp = 0L,
            flagSelection = null
        )

        // Configure DataStore to fire callback synchronously during DefaultFlagsRepository
        // construction, so the persistence latch is counted down before hasConfiguration() is called.
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
            flagSelection = null,
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
            initializationTimeoutScheduler = { _, _ -> {} }
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
    @ValueSource(strings = ["too_many_values", "tag_load_failed", "unknown"])
    fun `M log the reason and install W updateEvaluationsForContext() { flag selection ignored }`(
        fakeIgnoredReason: String
    ) {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(PrecomputedAssignmentsResponse(EMPTY_FLAGS_RESPONSE_JSON, fakeIgnoredReason))
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "flagSelection was ignored by the server ($fakeIgnoredReason). Loaded the full flag configuration."
        )
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.TELEMETRY,
            "Flag selection ignored",
            additionalProperties = mapOf("reason" to fakeIgnoredReason)
        )
        verify(mockFlagsRepository).setFlagsAndContext(eq(context), eq(emptyMap()), anyOrNull(), any())
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
    }

    @Test
    fun `M log the ignored reason and not install W updateEvaluationsForContext() { ignored reason, invalid body }`(
        @StringForgery fakeInvalidBody: String
    ) {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        ).thenReturn(PrecomputedAssignmentsResponse(fakeInvalidBody, "too_many_values"))
        whenever(mockPrecomputeMapper.map(fakeInvalidBody)).thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "flagSelection was ignored by the server (too_many_values). Loaded the full flag configuration."
        )
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.TELEMETRY,
            "Flag selection ignored",
            additionalProperties = mapOf("reason" to "too_many_values")
        )
        verify(mockFlagsRepository, never()).setFlagsAndContext(any(), any(), anyOrNull(), any())
        verify(mockFlagsStateManager).updateState(isA<FlagsClientState.Error>())
    }

    @Test
    fun `M not log a flag selection message W updateEvaluationsForContext() { no ignored reason }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        val messages = argumentCaptor<() -> String>()
        verify(mockInternalLogger, atLeast(0)).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            messages.capture(),
            anyOrNull(),
            any(),
            anyOrNull()
        )
        assertThat(messages.allValues.map { it() }).noneMatch { "flagSelection" in it || "Flag selection" in it }
        verify(mockFlagsRepository).setFlagsAndContext(eq(context), eq(emptyMap()), anyOrNull(), any())
    }

    @Test
    fun `M notify STALE W updateEvaluationsForContext() { failed fetch after an empty configuration }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockDataStore = mock<DataStoreHandler>()
        whenever(
            mockDataStore.value<FlagsStateEntry>(
                key = any(),
                version = anyOrNull(),
                callback = any(),
                deserializer = any()
            )
        ).doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onFailure()
            null
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        val realRepository = DefaultFlagsRepository(
            featureSdkCore = mockSdkCore,
            dataStore = mockDataStore,
            flagSelection = null,
            instanceName = "empty-configuration-test"
        )
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                eq(context),
                eq(fakeDatadogContext),
                isA<Diagnostics>()
            )
        )
            .thenReturn(EMPTY_FLAGS_RESPONSE, null)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(PrecomputedAssignments(emptyMap()))
        val testedManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = realRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} }
        )

        // When
        testedManager.updateEvaluationsForContext(context)
        testedManager.updateEvaluationsForContext(context)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
        }
    }

    private fun createRepository(): DefaultFlagsRepository {
        val mockDataStore = mock<DataStoreHandler>()
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onFailure()
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        return DefaultFlagsRepository(mockSdkCore, fakeTargetingKey, mockDataStore, null)
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
        initializationTimeoutScheduler = { _, _ -> {} }
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
        initializationTimeoutScheduler = scheduler
    )

    companion object {
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
        private val EMPTY_FLAGS_RESPONSE = PrecomputedAssignmentsResponse(EMPTY_FLAGS_RESPONSE_JSON, null)
    }
}
