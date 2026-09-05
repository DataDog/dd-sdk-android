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
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.utils.DDCoreStateHolder
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    lateinit var mockSdkCore: FeatureSdkCore

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

    private lateinit var mockWebServer: MockWebServer
    private lateinit var evaluationsManager: EvaluationsManager

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        evaluationsManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = InitializationTimeoutScheduler { _, _ -> {} }
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockFlagsFeatureScope
        whenever(
            mockFlagsFeatureScope.withContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        ) doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1).invoke(fakeDatadogContext)
        }

        // Mock executor to run tasks synchronously for testing
        whenever(mockExecutorService.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
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

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(mockResponse)
        whenever(mockPrecomputeMapper.map(mockResponse)).thenReturn(expectedFlags)

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(context, expectedFlags)
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

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(null)

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

        val invalidResponse = "{ invalid json }"
        whenever(
            mockAssignmentsDownloader.readPrecomputedFlags(
                context,
                fakeDatadogContext
            )
        ).thenReturn(invalidResponse)
        whenever(mockPrecomputeMapper.map(invalidResponse)).thenReturn(emptyMap())

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(context, emptyMap())
    }

    @Test
    fun `M log processing start W updateEvaluationsForContext() { any context }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(null)

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

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(mockResponse)
        whenever(mockPrecomputeMapper.map(mockResponse)).thenReturn(expectedFlags)

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
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(argThat { this is FlagsClientState.Error })
        }
    }

    @Test
    fun `M notify STALE W updateEvaluationsForContext() { network failure, cached flags, context matches }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())

        whenever(mockFlagsRepository.hasFlags()).thenReturn(true)
        whenever(mockFlagsRepository.getEvaluationContext()).thenReturn(publicContext)
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(null)

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
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(requestedContext, fakeDatadogContext))
            .thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(requestedContext)

        // Then
        inOrder(mockFlagsStateManager) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(argThat { this is FlagsClientState.Error })
        }
    }

    @Test
    fun `M invoke onSuccess W updateEvaluationsForContext() { success }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()
        val jsonResponse = EMPTY_FLAGS_RESPONSE_JSON
        val flagsMap = emptyMap<String, PrecomputedFlag>()

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(jsonResponse)
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(flagsMap)

        // When
        evaluationsManager.updateEvaluationsForContext(publicContext, callback = mockCallback)

        // Then
        verify(mockCallback).onSuccess()
    }

    @Test
    fun `M invoke onFailure W updateEvaluationsForContext() { network failure, no cached flags }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val mockCallback = mock<EvaluationContextCallback>()

        whenever(mockFlagsRepository.hasFlags()).thenReturn(false)
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(null)

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
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(null)

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

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(jsonResponse)
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(flagsMap)

        // When/Then - should not throw
        assertDoesNotThrow { evaluationsManager.updateEvaluationsForContext(publicContext, callback = null) }
    }

    @Test
    fun `M fail initialization callback W updateEvaluationsForContext() { timeout then operation completes late }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        var scheduledTimeoutMs: Long? = null
        var timeoutAction: (() -> Unit)? = null
        var operation: Runnable? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { timeoutMs, action ->
                scheduledTimeoutMs = timeoutMs
                timeoutAction = action
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)
        checkNotNull(timeoutAction).invoke()

        // Then
        assertThat(scheduledTimeoutMs).isEqualTo(2_500L)
        argumentCaptor<Throwable>().apply {
            verify(callback).onFailure(capture())
            assertThat(firstValue).isInstanceOf(FlagsInitializationTimeoutException::class.java)
            assertThat(firstValue.message).isEqualTo("Flags initialization timed out after 2500ms")
            verify(mockFlagsStateManager).updateState(FlagsClientState.Error(firstValue))
        }
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
            argThat { invoke().contains("configured timeout") },
            any<FlagsInitializationTimeoutException>(),
            eq(true),
            anyOrNull()
        )
        verify(callback, times(0)).onSuccess()

        // When
        checkNotNull(operation).run()

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        verify(callback, times(0)).onSuccess()
        verify(callback, times(1)).onFailure(any())
    }

    @Test
    fun `M keep ERROR state W updateEvaluationsForContext() { immediate timeout before completion }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        var operation: Runnable? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = 0,
            scheduler = InitializationTimeoutScheduler { _, action ->
                action()
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)

        // Then
        inOrder(mockFlagsStateManager, callback) {
            verify(mockFlagsStateManager).updateState(FlagsClientState.Reconciling)
            verify(mockFlagsStateManager).updateState(argThat { this is FlagsClientState.Error })
            verify(callback).onFailure(any<FlagsInitializationTimeoutException>())
        }
        verify(mockFlagsStateManager, times(0)).updateState(FlagsClientState.Ready)

        // When
        checkNotNull(operation).run()

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        verify(callback, times(0)).onSuccess()
    }

    @Test
    fun `M leave timeout task scheduled W updateEvaluationsForContext() { operation completes first }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)
        checkNotNull(timeoutAction).invoke()

        // Then
        verify(callback).onSuccess()
        verify(callback, times(0)).onFailure(any())
    }

    @Test
    fun `M invoke callback W updateEvaluationsForContext() { timeout state listener throws }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        val listenerFailure = IllegalStateException("listener failure")
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        whenever(mockFlagsStateManager.updateState(argThat { this is FlagsClientState.Error }))
            .thenThrow(listenerFailure)
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)
        val thrown = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            checkNotNull(timeoutAction).invoke()
        }

        // Then
        assertThat(thrown).isSameAs(listenerFailure)
        verify(callback).onFailure(any<FlagsInitializationTimeoutException>())
    }

    @Test
    fun `M invoke callback once W updateEvaluationsForContext() { timeout then late network failure }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        var operation: Runnable? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        whenever(mockFlagsRepository.hasFlags()).thenReturn(false)
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext)).thenReturn(null)
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)
        checkNotNull(timeoutAction).invoke()
        checkNotNull(operation).run()

        // Then
        argumentCaptor<Throwable>().apply {
            verify(callback, times(1)).onFailure(capture())
            assertThat(firstValue).isInstanceOf(FlagsInitializationTimeoutException::class.java)
        }
        verify(callback, times(0)).onSuccess()
    }

    @Test
    fun `M keep initialization timeout active W updateEvaluationsForContext() { decoding assignments }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenAnswer {
            checkNotNull(timeoutAction).invoke()
            emptyMap<String, PrecomputedFlag>()
        }
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)

        // Then
        verify(callback).onFailure(any<FlagsInitializationTimeoutException>())
        verify(callback, times(0)).onSuccess()
        verify(mockFlagsRepository).setFlagsAndContext(context, emptyMap())
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
    }

    @Test
    fun `M schedule timeout once W updateEvaluationsForContext() { context changes after initialization }`() {
        // Given
        val callback = mock<EvaluationContextCallback>()
        var scheduleCount = 0
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, _ ->
                scheduleCount += 1
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), callback)
        manager.updateEvaluationsForContext(EvaluationContext("second", emptyMap()), callback)

        // Then
        assertThat(scheduleCount).isEqualTo(1)
        verify(callback, times(2)).onSuccess()
    }

    @Test
    fun `M not schedule timeout W updateEvaluationsForContext() { initialization timeout is not configured }`() {
        // Given
        val callback = mock<EvaluationContextCallback>()
        var scheduleCount = 0
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = null,
            scheduler = InitializationTimeoutScheduler { _, _ ->
                scheduleCount += 1
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), callback)

        // Then
        assertThat(scheduleCount).isZero()
        verify(callback).onSuccess()
    }

    @Test
    fun `M update state to ERROR W updateEvaluationsForContext() { timeout and callback is null }`() {
        // Given
        var timeoutAction: (() -> Unit)? = null
        var operation: Runnable? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(EvaluationContext("first", emptyMap()), callback = null)
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
    fun `M complete once and recover to READY W updateEvaluationsForContext() { timeout races initialization }`() {
        // Given
        val iterations = 10_000
        val raceExecutor = Executors.newFixedThreadPool(2)
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())

        try {
            repeat(iterations) { iteration ->
                val operation = AtomicReference<Runnable>()
                whenever(mockExecutorService.execute(any())).thenAnswer {
                    operation.set(it.getArgument(0))
                    null
                }
                val timeoutAction = AtomicReference<() -> Unit>()
                val stateManager = FlagsStateManager(
                    DDCoreStateHolder.create(
                        initialState = FlagsClientState.NotReady,
                        onStateChanged = FlagsStateListener::onStateChanged
                    )
                )
                val manager = EvaluationsManager(
                    sdkCore = mockSdkCore,
                    executorService = mockExecutorService,
                    internalLogger = mockInternalLogger,
                    flagsRepository = mockFlagsRepository,
                    assignmentsReader = mockAssignmentsDownloader,
                    precomputeMapper = mockPrecomputeMapper,
                    flagStateManager = stateManager,
                    initializationTimeoutMs = 2_500L,
                    initializationTimeoutScheduler = InitializationTimeoutScheduler { _, action ->
                        timeoutAction.set(action)
                    }
                )
                val callbackCount = AtomicInteger()
                val successfulCallback = AtomicReference<Boolean>()
                val stateAtCallback = AtomicReference<FlagsClientState>()
                val callback = object : EvaluationContextCallback {
                    override fun onSuccess() {
                        callbackCount.incrementAndGet()
                        stateAtCallback.set(stateManager.getCurrentState())
                        successfulCallback.set(true)
                    }

                    override fun onFailure(error: Throwable) {
                        callbackCount.incrementAndGet()
                        stateAtCallback.set(stateManager.getCurrentState())
                        successfulCallback.set(false)
                    }
                }
                manager.updateEvaluationsForContext(context, callback)

                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val finished = CountDownLatch(2)
                val raceFailure = AtomicReference<Throwable>()
                listOf(
                    checkNotNull(operation.get()),
                    Runnable { checkNotNull(timeoutAction.get()).invoke() }
                ).forEach { action ->
                    raceExecutor.execute {
                        ready.countDown()
                        try {
                            start.await()
                            action.run()
                        } catch (error: Throwable) {
                            raceFailure.compareAndSet(null, error)
                        } finally {
                            finished.countDown()
                        }
                    }
                }

                // When
                assertThat(ready.await(2, TimeUnit.SECONDS)).describedAs("Iteration $iteration").isTrue()
                start.countDown()

                // Then
                assertThat(finished.await(2, TimeUnit.SECONDS)).describedAs("Iteration $iteration").isTrue()
                assertThat(raceFailure.get()).describedAs("Iteration $iteration").isNull()
                assertThat(callbackCount.get()).describedAs("Iteration $iteration").isEqualTo(1)
                assertThat(successfulCallback.get()).describedAs("Iteration $iteration").isNotNull()
                if (successfulCallback.get() == true) {
                    assertThat(stateAtCallback.get()).describedAs("Iteration $iteration")
                        .isEqualTo(FlagsClientState.Ready)
                } else {
                    // A timeout publishes Error before its callback, but the intentionally unblocked
                    // late success may recover to Ready before the callback reads the state.
                    val observedState = stateAtCallback.get()
                    assertThat(observedState is FlagsClientState.Error || observedState == FlagsClientState.Ready)
                        .describedAs("Iteration $iteration")
                        .isTrue()
                }
                assertThat(stateManager.getCurrentState()).describedAs("Iteration $iteration")
                    .isEqualTo(FlagsClientState.Ready)
            }
        } finally {
            raceExecutor.shutdownNow()
            assertThat(raceExecutor.awaitTermination(2, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `M let first caller own timeout W updateEvaluationsForContext() { reentrant state listener }`() {
        // Given
        val firstContext = EvaluationContext("first", emptyMap())
        val nestedContext = EvaluationContext("nested", emptyMap())
        var timeoutAction: (() -> Unit)? = null
        val firstFailure = AtomicReference<Throwable>()
        val nestedFailure = AtomicReference<Throwable>()
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        lateinit var manager: EvaluationsManager
        var didStartNestedCall = false
        stateManager.addListener(
            object : FlagsStateListener {
                override fun onStateChanged(newState: FlagsClientState) {
                    if (newState == FlagsClientState.Reconciling && !didStartNestedCall) {
                        didStartNestedCall = true
                        manager.updateEvaluationsForContext(
                            nestedContext,
                            callbackRecordingFailure(nestedFailure)
                        )
                    }
                }
            }
        )
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        manager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            initializationTimeoutScheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(firstContext, callbackRecordingFailure(firstFailure))
        checkNotNull(timeoutAction).invoke()

        // Then
        assertThat(firstFailure.get()).isInstanceOf(FlagsInitializationTimeoutException::class.java)
        assertThat(nestedFailure.get()).isNull()
    }

    @Test
    fun `M publish late ready W updateEvaluationsForContext() { timeout callback blocks }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        var operation: Runnable? = null
        var timeoutAction: (() -> Unit)? = null
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val operationFinished = CountDownLatch(1)
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operation = it.getArgument(0)
            null
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = Unit

            override fun onFailure(error: Throwable) {
                callbackStarted.countDown()
                releaseCallback.await()
            }
        }
        manager.updateEvaluationsForContext(context, callback)

        // When
        val timeoutThread = Thread { checkNotNull(timeoutAction).invoke() }
        timeoutThread.start()
        assertThat(callbackStarted.await(1, TimeUnit.SECONDS)).isTrue()
        Thread {
            checkNotNull(operation).run()
            operationFinished.countDown()
        }.start()
        val completedWhileCallbackWasBlocked = operationFinished.await(250, TimeUnit.MILLISECONDS)
        releaseCallback.countDown()
        timeoutThread.join(1_000)

        // Then
        assertThat(completedWhileCallbackWasBlocked).isTrue()
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
    }

    @Test
    fun `M release timeout scheduler W updateEvaluationsForContext() { timeout callback blocks }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        var timeoutAction: (() -> Unit)? = null
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val timeoutActionReturned = CountDownLatch(1)
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            },
            callbackDispatcher = InitializationTimeoutCallbackDispatcher { action -> Thread(action).start() }
        )
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = Unit

            override fun onFailure(error: Throwable) {
                callbackStarted.countDown()
                releaseCallback.await()
            }
        }
        manager.updateEvaluationsForContext(context, callback)

        // When
        val timeoutThread = Thread {
            checkNotNull(timeoutAction).invoke()
            timeoutActionReturned.countDown()
        }
        timeoutThread.start()
        val didStartCallback = callbackStarted.await(1, TimeUnit.SECONDS)
        val didReturnWhileBlocked = timeoutActionReturned.await(250, TimeUnit.MILLISECONDS)
        releaseCallback.countDown()
        timeoutThread.join(1_000)

        // Then
        assertThat(didStartCallback).isTrue()
        assertThat(didReturnWhileBlocked).isTrue()
        assertThat(timeoutThread.isAlive).isFalse()
    }

    @Test
    fun `M ignore late success W updateEvaluationsForContext() { newer context is pending }`() {
        // Given
        val contextA = EvaluationContext("user-A", emptyMap())
        val contextB = EvaluationContext("user-B", emptyMap())
        val operations = mutableListOf<Runnable>()
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operations += it.getArgument<Runnable>(0)
            null
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(contextA)
        checkNotNull(timeoutAction).invoke()
        manager.updateEvaluationsForContext(contextB)
        operations[0].run()

        // Then
        verify(mockFlagsRepository, times(0)).setFlagsAndContext(eq(contextA), any())

        // When
        operations[1].run()

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(eq(contextB), any())
    }

    @Test
    fun `M reach terminal state W updateEvaluationsForContext() { concurrent submissions reorder reconciling }`() {
        // Given
        val contextA = EvaluationContext("user-A", emptyMap())
        val contextB = EvaluationContext("user-B", emptyMap())
        val firstReconcilingStarted = CountDownLatch(1)
        val releaseFirstReconciling = CountDownLatch(1)
        val reconcilingCount = AtomicInteger()
        val observedState = AtomicReference<FlagsClientState>(FlagsClientState.NotReady)
        whenever(mockFlagsStateManager.updateState(any())).thenAnswer { invocation ->
            val state = invocation.getArgument<FlagsClientState>(0)
            if (state == FlagsClientState.Reconciling && reconcilingCount.incrementAndGet() == 1) {
                firstReconcilingStarted.countDown()
                assertThat(releaseFirstReconciling.await(2, TimeUnit.SECONDS)).isTrue()
            }
            observedState.set(state)
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(scheduler = InitializationTimeoutScheduler { _, _ -> {} })

        // When
        val firstSubmission = Thread { manager.updateEvaluationsForContext(contextA) }
        firstSubmission.start()
        assertThat(firstReconcilingStarted.await(1, TimeUnit.SECONDS)).isTrue()
        manager.updateEvaluationsForContext(contextB)
        releaseFirstReconciling.countDown()
        firstSubmission.join(1_000)

        // Then
        assertThat(firstSubmission.isAlive).isFalse()
        assertThat(observedState.get()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M publish context before success W updateEvaluationsForContext() { ordinary calls overlap }`() {
        // Given
        val contextA = EvaluationContext("user-A", emptyMap())
        val contextB = EvaluationContext("user-B", emptyMap())
        val operations = mutableListOf<Runnable>()
        val callbackA = mock<EvaluationContextCallback>()
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operations += it.getArgument<Runnable>(0)
            null
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(scheduler = InitializationTimeoutScheduler { _, _ -> {} })

        // When
        manager.updateEvaluationsForContext(contextA, callbackA)
        manager.updateEvaluationsForContext(contextB)
        operations[0].run()

        // Then
        inOrder(mockFlagsRepository, callbackA) {
            verify(mockFlagsRepository).setFlagsAndContext(eq(contextA), any())
            verify(callbackA).onSuccess()
        }
    }

    @Test
    fun `M keep ready state W updateEvaluationsForContext() { superseded timeout fires }`() {
        // Given
        val contextA = EvaluationContext("user-A", emptyMap())
        val contextB = EvaluationContext("user-B", emptyMap())
        val operations = mutableListOf<Runnable>()
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer {
            operations += it.getArgument<Runnable>(0)
            null
        }
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        val manager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            initializationTimeoutScheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(contextA)
        manager.updateEvaluationsForContext(contextB)
        operations[1].run()
        checkNotNull(timeoutAction).invoke()

        // Then
        assertThat(stateManager.getCurrentState()).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M complete timeout without waiting W updateEvaluationsForContext() { persistence load is pending }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val dataStore = mock<DataStoreHandler>()
        val persistenceCallback = AtomicReference<DataStoreReadCallback<FlagsStateEntry>>()
        doAnswer {
            persistenceCallback.set(it.getArgument(2))
            null
        }.whenever(dataStore).value<FlagsStateEntry>(
            key = any(),
            version = anyOrNull(),
            callback = any(),
            deserializer = any()
        )
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        val repository = DefaultFlagsRepository(
            featureSdkCore = mockSdkCore,
            instanceName = "pending-timeout",
            dataStore = dataStore,
            persistenceLoadTimeoutMs = 5_000L
        )
        val stateManager = FlagsStateManager(
            DDCoreStateHolder.create(
                initialState = FlagsClientState.NotReady,
                onStateChanged = FlagsStateListener::onStateChanged
            )
        )
        var timeoutAction: (() -> Unit)? = null
        val timeoutFailure = AtomicReference<Throwable>()
        val timeoutFinished = CountDownLatch(1)
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        val manager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = repository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = stateManager,
            initializationTimeoutMs = 2_500L,
            initializationTimeoutScheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )
        manager.updateEvaluationsForContext(context, callbackRecordingFailure(timeoutFailure))
        val timeoutThread = Thread {
            checkNotNull(timeoutAction).invoke()
            timeoutFinished.countDown()
        }

        try {
            // When
            timeoutThread.start()

            // Then
            assertThat(timeoutFinished.await(250, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(timeoutFailure.get()).isInstanceOf(FlagsInitializationTimeoutException::class.java)
            assertThat(stateManager.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        } finally {
            persistenceCallback.get().onFailure()
            timeoutThread.join(1_000)
        }
    }

    @Test
    fun `M publish stale W updateEvaluationsForContext() { timeout with matching cache }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        whenever(mockFlagsRepository.hasFlagsForContext(context)).thenReturn(true)
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(context)
        checkNotNull(timeoutAction).invoke()

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Stale)
    }

    @Test
    fun `M clear mismatched cache W updateEvaluationsForContext() { timeout }`() {
        // Given
        val requestedContext = EvaluationContext("requested", emptyMap())
        var timeoutAction: (() -> Unit)? = null
        whenever(mockExecutorService.execute(any())).thenAnswer { null }
        whenever(mockFlagsRepository.hasFlagsForContext(requestedContext)).thenReturn(false)
        val manager = createManager(
            initializationTimeoutMs = 2_500L,
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                {}
            }
        )

        // When
        manager.updateEvaluationsForContext(requestedContext)
        checkNotNull(timeoutAction).invoke()

        // Then
        verify(mockFlagsRepository).clear()
        argumentCaptor<FlagsClientState>().apply {
            verify(mockFlagsStateManager, times(2)).updateState(capture())
            assertThat(allValues.last()).isInstanceOf(FlagsClientState.Error::class.java)
        }
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
            initializationTimeoutScheduler = InitializationTimeoutScheduler { _, _ -> {} }
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

        whenever(mockAssignmentsDownloader.readPrecomputedFlags(publicContext, fakeDatadogContext))
            .thenReturn(jsonResponse)
        whenever(mockPrecomputeMapper.map(jsonResponse)).thenReturn(flagsMap)

        // When
        evaluationsManagerWithRealState.updateEvaluationsForContext(publicContext, callback = callback)

        // Then
        assertThat(stateWhenCallbackInvoked).isEqualTo(FlagsClientState.Ready)
    }

    // region Cold-start integration

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

        val realRepository = DefaultFlagsRepository(
            featureSdkCore = mockSdkCore,
            dataStore = mockDataStore,
            instanceName = "integration-test"
        )

        // Network call returns null (simulates network failure)
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(null)

        val integrationManager = EvaluationsManager(
            sdkCore = mockSdkCore,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = realRepository,
            assignmentsReader = mockAssignmentsDownloader,
            precomputeMapper = mockPrecomputeMapper,
            flagStateManager = mockFlagsStateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = InitializationTimeoutScheduler { _, _ -> {} }
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

    private fun createManager(
        initializationTimeoutMs: Long? = null,
        scheduler: InitializationTimeoutScheduler,
        callbackDispatcher: InitializationTimeoutCallbackDispatcher =
            InitializationTimeoutCallbackDispatcher { it() }
    ): EvaluationsManager = EvaluationsManager(
        sdkCore = mockSdkCore,
        executorService = mockExecutorService,
        internalLogger = mockInternalLogger,
        flagsRepository = mockFlagsRepository,
        assignmentsReader = mockAssignmentsDownloader,
        precomputeMapper = mockPrecomputeMapper,
        flagStateManager = mockFlagsStateManager,
        initializationTimeoutMs = initializationTimeoutMs,
        initializationTimeoutScheduler = scheduler,
        initializationTimeoutCallbackDispatcher = callbackDispatcher
    )

    private fun callbackRecordingFailure(target: AtomicReference<Throwable>): EvaluationContextCallback =
        object : EvaluationContextCallback {
            override fun onSuccess() = Unit

            override fun onFailure(error: Throwable) {
                target.set(error)
            }
        }

    companion object {
        private const val EMPTY_FLAGS_RESPONSE_JSON = "{\"data\": {\"attributes\": {\"flags\": {}}}}"
    }
}
