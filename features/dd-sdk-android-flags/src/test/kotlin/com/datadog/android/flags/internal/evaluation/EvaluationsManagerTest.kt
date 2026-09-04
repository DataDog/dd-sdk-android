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
import java.util.concurrent.ExecutorService

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
            initializationTimeoutMs = 30_000L,
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
    fun `M fail initialization callback W timeout { operation completes late }`() {
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
                {}
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
        }
        verify(callback, times(0)).onSuccess()

        // When
        checkNotNull(operation).run()

        // Then
        verify(mockFlagsStateManager).updateState(FlagsClientState.Ready)
        verify(callback, times(0)).onSuccess()
        verify(callback, times(1)).onFailure(any())
    }

    @Test
    fun `M cancel initialization timeout W operation completes first`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey, emptyMap())
        val callback = mock<EvaluationContextCallback>()
        var timeoutAction: (() -> Unit)? = null
        var cancellationCount = 0
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(context, fakeDatadogContext))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
            scheduler = InitializationTimeoutScheduler { _, action ->
                timeoutAction = action
                { cancellationCount += 1 }
            }
        )

        // When
        manager.updateEvaluationsForContext(context, callback)
        checkNotNull(timeoutAction).invoke()

        // Then
        assertThat(cancellationCount).isEqualTo(1)
        verify(callback).onSuccess()
        verify(callback, times(0)).onFailure(any())
    }

    @Test
    fun `M keep initialization timeout active W decoding assignments`() {
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
    fun `M schedule timeout once W context changes after initialization`() {
        // Given
        val callback = mock<EvaluationContextCallback>()
        var scheduleCount = 0
        whenever(mockAssignmentsDownloader.readPrecomputedFlags(any(), eq(fakeDatadogContext)))
            .thenReturn(EMPTY_FLAGS_RESPONSE_JSON)
        whenever(mockPrecomputeMapper.map(EMPTY_FLAGS_RESPONSE_JSON)).thenReturn(emptyMap())
        val manager = createManager(
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
            initializationTimeoutMs = 30_000L,
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
            initializationTimeoutMs = 30_000L,
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
        initializationTimeoutMs: Long = 30_000L,
        scheduler: InitializationTimeoutScheduler
    ): EvaluationsManager = EvaluationsManager(
        sdkCore = mockSdkCore,
        executorService = mockExecutorService,
        internalLogger = mockInternalLogger,
        flagsRepository = mockFlagsRepository,
        assignmentsReader = mockAssignmentsDownloader,
        precomputeMapper = mockPrecomputeMapper,
        flagStateManager = mockFlagsStateManager,
        initializationTimeoutMs = initializationTimeoutMs,
        initializationTimeoutScheduler = scheduler
    )

    companion object {
        private const val EMPTY_FLAGS_RESPONSE_JSON = "{\"data\": {\"attributes\": {\"flags\": {}}}}"
    }
}
