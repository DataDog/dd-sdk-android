/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.ProfilingManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.core.sampling.DeterministicSampler
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.rum.RumSessionConstants
import com.datadog.android.internal.sampling.SessionSamplingIdProvider
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerCallback
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingRequestFactory
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.ProfilingWriter
import com.datadog.android.profiling.internal.anr.AnrTriggerRegistrar
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.quota.NoOpQuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaReason
import com.datadog.android.profiling.internal.quota.QuotaResult
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetryEvent
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import com.datadog.android.profiling.utils.config.MainLooperTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ProfilingFeatureTest {

    private lateinit var testedFeature: ProfilingFeature

    @Mock
    private lateinit var mockSdkCore: InternalSdkCore

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockProfilingExecutor: ExecutorService

    @Mock
    private lateinit var mockSchedulerExecutor: ScheduledExecutorService

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockService: ProfilingManager

    @Mock
    private lateinit var mockProfiler: Profiler

    @Mock
    private lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    private lateinit var mockProfilingFeatureScope: FeatureScope

    @Mock
    private lateinit var mockDataWriter: ProfilingWriter

    @Mock
    private lateinit var mockQuotaChecker: QuotaChecker

    @Mock
    private lateinit var mockCallFactory: Call.Factory

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockSharedPreferencesStorage: SharedPreferencesStorage

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockMutableTimeProvider: MutableTimeProvider

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    @Mock
    private lateinit var mockPackageManager: PackageManager

    @Mock
    private lateinit var mockAnrTriggerRegistrar: AnrTriggerRegistrar

    @Mock
    private lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Forgery
    private lateinit var fakeConfiguration: ProfilingConfiguration

    @StringForgery
    private lateinit var fakeSessionId: String

    @Forgery
    private lateinit var fakeTTID: ProfilerEvent.RumVitalEvent

    @Forgery
    private lateinit var fakeRumLongTaskEvent: ProfilerEvent.RumLongTaskEvent

    @Forgery
    private lateinit var fakeRumAnrEvent: ProfilerEvent.RumAnrEvent

    @StringForgery
    private lateinit var fakeInstanceName: String

    @LongForgery(min = 1L)
    private var fakeProfilingPackageVersionCode: Long = 0L

    @Forgery
    private lateinit var fakeDatadogContext: DatadogContext

    private val fakeAllSampledConfiguration = ProfilingConfiguration(
        customEndpointUrl = null,
        applicationLaunchSampleRate = 100f,
        continuousSampleRate = 100f
    )

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockContext.packageManager) doReturn mockPackageManager
        val mockPackageInfo = mock<PackageInfo> {
            on { longVersionCode } doReturn fakeProfilingPackageVersionCode
        }
        whenever(
            mockPackageManager.getPackageInfo(
                "com.google.android.profiling",
                PackageManager.MATCH_APEX
            )
        ) doReturn mockPackageInfo
        whenever(mockSdkCore.name) doReturn fakeInstanceName
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockProfilingExecutor
        whenever(mockProfiler.timeProvider) doReturn mockMutableTimeProvider
        whenever(mockSdkCore.createOkHttpCallFactory(any())) doReturn mockCallFactory
        whenever(mockProfiler.scheduledExecutorService) doReturn mockSchedulerExecutor
        whenever(mockContext.getSystemService(ProfilingManager::class.java)) doReturn (mockService)
        whenever(mockContext.getSharedPreferences(any(), any())) doReturn mockSharedPreferences
        whenever(mockSharedPreferences.edit()) doReturn mockEditor
        whenever(mockEditor.putBoolean(any(), any())) doReturn mockEditor
        whenever(mockEditor.putInt(any(), any())) doReturn mockEditor
        whenever(mockEditor.putString(any(), any())) doReturn mockEditor
        whenever(mockEditor.putStringSet(any(), any())) doReturn mockEditor
        whenever(mockEditor.putFloat(any(), any())) doReturn mockEditor
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingFeatureScope
        whenever(mockProfilingFeatureScope.withContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1).invoke(fakeDatadogContext)
        }
        testedFeature = ProfilingFeature(mockSdkCore, fakeConfiguration, mockProfiler)
        ProfilingStorage.sharedPreferencesStorage = mockSharedPreferencesStorage
        fakeTTID = fakeTTID.copy(type = ProfilerEvent.RumVitalEvent.Type.TTID)
    }

    @AfterEach
    fun `tear down`() {
        ProfilingStorage.sharedPreferencesStorage = null
    }

    @Test
    fun `M allow 18h storage W init()`() {
        // When
        val config = testedFeature.storageConfiguration

        // Then
        assertThat(config.oldBatchThreshold).isEqualTo(18L * 60L * 60L * 1000L)
    }

    @Test
    fun `M limit batch to single event W init()`() {
        // When
        val config = testedFeature.storageConfiguration

        // Then
        assertThat(config.maxItemsPerBatch).isEqualTo(1)
    }

    @Test
    fun `M initialize ProfilingRequestFactory W initialize()`() {
        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(testedFeature.requestFactory).isInstanceOf(ProfilingRequestFactory::class.java)
    }

    @Test
    fun `M bind profiler to the SDK core W initialize()`() {
        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockProfiler).internalLogger = mockInternalLogger
        verify(mockProfiler.timeProvider).delegate = mockTimeProvider
    }

    @Test
    fun `M report the package version code W initialize() {telemetry reported before init}`(
        @IntForgery(min = 1, max = 8) fakeErrorCode: Int,
        @StringForgery fakeErrorMessage: String
    ) {
        // Given a profiling session that ended before the SDK was initialized: the profiler has
        // no logger yet, so its telemetry is buffered and only dispatched on initialization.
        val profilingTelemetry = ProfilingTelemetry()
        val profiler = PerfettoProfiler(
            timeProvider = MutableTimeProvider.create(mockTimeProvider),
            scheduledExecutorService = mockSchedulerExecutor,
            profilingTelemetry = profilingTelemetry,
            anrTriggerRegistrar = mockAnrTriggerRegistrar,
            buildSdkVersionProvider = mockBuildSdkVersionProvider
        )
        profilingTelemetry.report(
            ProfilingTelemetryEvent.SessionEnd(
                startReason = ProfilingStartReason.APPLICATION_LAUNCH.value,
                appStartInfo = null,
                errorCode = fakeErrorCode,
                errorMessage = fakeErrorMessage,
                fileSize = 0L,
                durationMs = 0L,
                resultCallbackDelayMs = 0L,
                clientClockDriftMs = 0L,
                stopReason = ProfilingTelemetry.STOPPED_REASON_ERROR,
                bufferSizeKb = 0,
                samplingFrequencyHz = 0
            )
        )
        val feature = ProfilingFeature(
            sdkCore = mockSdkCore,
            // continuous profiling disabled, so initialization doesn't schedule anything
            configuration = fakeConfiguration.copy(continuousSampleRate = 0f),
            profiler = profiler
        )

        // When
        feature.onInitialize(mockContext)

        // Then
        val propertiesCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).logMetric(
            any(),
            propertiesCaptor.capture(),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        val profilingConfig = propertiesCaptor.firstValue["profiling_config"] as Map<*, *>
        assertThat(profilingConfig["profiling_package_version_code"])
            .isEqualTo(fakeProfilingPackageVersionCode)
    }

    @Test
    fun `M set Profiling sample rate W initialize()`(
        @FloatForgery(min = 0f, max = 100f) fakeStoredSampleRate: Float
    ) {
        // Given
        // Whatever was previously stored, only one SDK instance can initialize the feature, so the
        // configured sample rate always wins.
        whenever(
            mockSharedPreferencesStorage
                .getFloat("dd_profiling_sample_rate", -1f)
        ) doReturn fakeStoredSampleRate

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockSharedPreferencesStorage).putFloat(
            "dd_profiling_sample_rate",
            fakeConfiguration.applicationLaunchSampleRate
        )
    }

    @Test
    fun `M stop Profiling W receive TTID event {continuous disabled}`() {
        // Given — continuous disabled, profiler not running (no active launch session)
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = 0f
            ),
            mockProfiler
        )
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then
        verify(mockProfiler).stop()
    }

    @Test
    fun `M not stop Profiling W receive TTID event {current session sampled in}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession(UUID.randomUUID().toString(), 100f)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then — scheduler takes over, profiler is NOT stopped here
        verify(mockProfiler, never()).stop()
    }

    @Test
    fun `M stop Profiling W receive TTID event {continuous enabled, no session renewal yet}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then
        verify(mockProfiler).stop()
    }

    @Test
    fun `M stop Profiling W receive TTID event {continuous enabled, session sampled out}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        testedFeature.dispatchRumSession(UUID.randomUUID().toString(), 0f)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then
        verify(mockProfiler).stop()
    }

    @Test
    fun `M forward sessionId to scheduler W onContextUpdate {RUM session in feature context}`(
        forge: Forge
    ) {
        // Given — forge both rates; pre-compute the expected decision via a reference
        // sampler using the same idConverter and the expected effective rate.
        val fakeSessionRate = forge.aFloat(min = 0.1f, max = 100f)
        val fakeContinuousRate = forge.aFloat(min = 0.1f, max = 100f)
        val realSessionId = UUID.randomUUID().toString()
        val expectedEffectiveRate =
            (fakeSessionRate * fakeContinuousRate / 100f).coerceIn(0f, 100f)
        val expectedDecision = DeterministicSampler<String>(
            SessionSamplingIdProvider::provideId,
            expectedEffectiveRate
        ).sample(realSessionId)
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = fakeContinuousRate
            ),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.dispatchRumSession(realSessionId, fakeSessionRate)

        // Then
        val scheduler = checkNotNull(testedFeature.continuousProfilingScheduler)
        assertThat(scheduler.currentSessionId).isEqualTo(realSessionId)
        assertThat(scheduler.currentSessionSampled).isEqualTo(expectedDecision)
        assertThat(testedFeature.lastSeenRumSessionId).isEqualTo(realSessionId)
    }

    @Test
    fun `M register as context update receiver W onInitialize()`() {
        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockSdkCore).setContextUpdateReceiver(testedFeature)
    }

    @Test
    fun `M ignore context update W onContextUpdate {non-RUM feature}`(
        @StringForgery fakeOtherFeatureName: String,
        @FloatForgery(min = 0f, max = 100f) fakeSessionRate: Float
    ) {
        // Given
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onContextUpdate(
            fakeOtherFeatureName,
            mapOf(
                FeatureContextKeys.RUM_SESSION_ID to UUID.randomUUID().toString(),
                FeatureContextKeys.RUM_SESSION_SAMPLE_RATE to fakeSessionRate
            )
        )

        // Then
        assertThat(testedFeature.lastSeenRumSessionId).isNull()
    }

    @Test
    fun `M ignore context update W onContextUpdate {session id is NULL_UUID sentinel}`(
        @FloatForgery(min = 0f, max = 100f) fakeSessionRate: Float
    ) {
        // Given — RUM has been initialised but no session has been created yet
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                FeatureContextKeys.RUM_SESSION_ID to RumSessionConstants.EMPTY_RUM_SESSION_ID,
                FeatureContextKeys.RUM_SESSION_SAMPLE_RATE to fakeSessionRate
            )
        )

        // Then
        assertThat(testedFeature.lastSeenRumSessionId).isNull()
    }

    @Test
    fun `M sample session out W onContextUpdate {session_sample_rate missing from context}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        val sessionId = UUID.randomUUID().toString()

        // When
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_SESSION_ID to sessionId)
        )

        // Then
        val scheduler = checkNotNull(testedFeature.continuousProfilingScheduler)
        assertThat(testedFeature.lastSeenRumSessionId).isEqualTo(sessionId)
        assertThat(scheduler.currentSessionId).isEqualTo(sessionId)
        assertThat(scheduler.currentSessionSampled).isFalse()
    }

    @Test
    fun `M only forward once W onContextUpdate {same session id repeated}`(
        forge: Forge
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        val sessionId = UUID.randomUUID().toString()
        val firstSampleRate = forge.aFloat(min = 0.1f, max = 100f)
        val secondSampleRate = forge.aFloat(min = 0.1f, max = 100f)
        testedFeature.dispatchRumSession(sessionId, firstSampleRate)
        val scheduler = checkNotNull(testedFeature.continuousProfilingScheduler)
        val sampledAfterFirst = scheduler.currentSessionSampled

        // When — same session id arrives again with a different sample rate (e.g. spurious
        // RUM context update emitted by an unrelated view change). The receiver should
        // ignore it without recomputing sampling.
        testedFeature.dispatchRumSession(sessionId, secondSampleRate)

        // Then
        assertThat(scheduler.currentSessionSampled).isEqualTo(sampledAfterFirst)
    }

    @Test
    fun `M unregister context receiver and clear last session W onStop()`(
        @FloatForgery(min = 0.1f, max = 100f) fakeSessionRate: Float
    ) {
        // Given
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession(UUID.randomUUID().toString(), fakeSessionRate)

        // When
        testedFeature.onStop()

        // Then
        verify(mockSdkCore).removeContextUpdateReceiver(testedFeature)
        assertThat(testedFeature.lastSeenRumSessionId).isNull()
    }

    @Test
    fun `M reset quota checker W onStop()`() {
        // Given
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.onStop()

        // Then
        verify(mockQuotaChecker).reset()
        assertThat(testedFeature.quotaChecker).isInstanceOf(NoOpQuotaChecker::class.java)
    }

    @Test
    fun `M start continuous cycle W profiler result received {TTID session unsampled}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )

        testedFeature.dispatchRumSession("session-id", 100f)
        testedFeature.simulateQuotaAllowed()

        testedFeature.onReceive(ProfilerEvent.TTIDNotTracked)

        val runnableCaptor = argumentCaptor<Runnable>()

        // When
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1000L,
                resultFilePath = "/fake/path"
            )
        )

        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler).start(
            appContext = eq(mockContext),
            startReason = eq(ProfilingStartReason.CONTINUOUS),
            additionalAttributes = any(),
            durationMs = any()
        )
    }

    @Test
    fun `M start continuous cycle W profiler failure received {APPLICATION_LAUNCH tag}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        testedFeature.onReceive(ProfilerEvent.TTIDNotTracked)

        val runnableCaptor = argumentCaptor<Runnable>()

        // When
        callbackCaptor.firstValue.onFailure(ProfilingStartReason.APPLICATION_LAUNCH)

        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler).start(
            appContext = eq(mockContext),
            startReason = eq(ProfilingStartReason.CONTINUOUS),
            additionalAttributes = any(),
            durationMs = any()
        )
    }

    @Test
    fun `M not start continuous cycle W profiler failure received {CONTINUOUS tag}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )

        // When
        callbackCaptor.firstValue.onFailure(ProfilingStartReason.CONTINUOUS)

        // Then
        verify(mockProfiler, never()).start(
            appContext = any(),
            startReason = any(),
            additionalAttributes = any(),
            durationMs = any()
        )
    }

    @Test
    fun `M write with empty events W continuous profiling result received {no RUM events}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS),
            longTasks = emptyList(),
            anrEvents = emptyList(),
            vitalEvents = emptyList()
        )
        val logCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger, atLeastOnce()).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.USER),
            logCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(logCaptor.allValues.map { it.invoke() })
            .contains("Continuous profiling result not uploaded: no pending RUM events.")
    }

    @Test
    fun `M write event W continuous profiling result received {RUM long task events present}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        // Open the continuous accumulation window
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )
        // Run the cooldown runnable to open the active window (sets isActive = true)
        val cooldownRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(cooldownRunnableCaptor.capture(), any(), any())
        cooldownRunnableCaptor.firstValue.run()
        testedFeature.onReceive(fakeRumLongTaskEvent)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS),
            longTasks = listOf(fakeRumLongTaskEvent),
            anrEvents = emptyList(),
            vitalEvents = emptyList()
        )
    }

    @Test
    fun `M write event W continuous profiling result received {RUM ANR events present}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        // Open the continuous accumulation window
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )
        // Run the cooldown runnable to open the active window (sets isActive = true)
        val cooldownRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(cooldownRunnableCaptor.capture(), any(), any())
        cooldownRunnableCaptor.firstValue.run()
        testedFeature.onReceive(fakeRumAnrEvent)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS),
            longTasks = emptyList(),
            anrEvents = listOf(fakeRumAnrEvent),
            vitalEvents = emptyList()
        )
    }

    @Test
    fun `M accumulate RUM events in feature lists W continuous active window open`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        // Close launch window
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1L,
                resultFilePath = "/fake"
            )
        )
        // Open continuous active window
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())
        runnableCaptor.firstValue.run()

        // When
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).containsExactly(fakeRumLongTaskEvent)
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).containsExactly(fakeRumAnrEvent)
    }

    @Test
    fun `M not accumulate RUM events W no profiling window is active {between windows}`() {
        // Given
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = 0f
            ),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M accumulate vital event W onReceive {launch profiling active}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingVitalEvents).containsExactly(fakeTTID)
    }

    @Test
    fun `M accumulate vital event W onReceive {continuous active window open}`(
        @Forgery fakeContinuousVital: ProfilerEvent.RumVitalEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        // Close launch window
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1L,
                resultFilePath = "/fake"
            )
        )
        // Open continuous active window
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())
        runnableCaptor.firstValue.run()

        // When
        testedFeature.onReceive(fakeContinuousVital)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingVitalEvents).containsExactly(fakeContinuousVital)
    }

    @Test
    fun `M not accumulate vital event W onReceive {no profiling window is active}`() {
        // Given
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = 0f
            ),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingVitalEvents).isEmpty()
    }

    @Test
    fun `M not stop Profiling W receive OPERATION vital event {continuous disabled}`(
        @Forgery fakeOperationVital: ProfilerEvent.RumVitalEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = 0f
            ),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(
            fakeOperationVital.copy(type = ProfilerEvent.RumVitalEvent.Type.OPERATION)
        )

        // Then
        verify(mockProfiler, never()).stop()
    }

    @Test
    fun `M not stop Profiling W receive TTFD vital event {continuous disabled}`(
        @Forgery fakeTtfdVital: ProfilerEvent.RumVitalEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = 0f
            ),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(
            fakeTtfdVital.copy(type = ProfilerEvent.RumVitalEvent.Type.TTFD)
        )

        // Then
        verify(mockProfiler, never()).stop()
    }

    @Test
    fun `M not write launch event W app-launch profiling result received {only OPERATION vital, no TTID}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @Forgery fakeOperationVital: ProfilerEvent.RumVitalEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(
            fakeOperationVital.copy(type = ProfilerEvent.RumVitalEvent.Type.OPERATION)
        )

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M clear RUM events W new continuous active window starts`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        // Close launch window
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1L,
                resultFilePath = "/fake"
            )
        )
        // Open window 1
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(
            runnableCaptor.capture(),
            any(),
            any()
        )
        runnableCaptor.lastValue.run() // fires cooldown → opens window 1
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isNotEmpty()
        // End window 1
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.CONTINUOUS,
                end = 1L,
                resultFilePath = "/fake"
            )
        )

        // When
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(
            runnableCaptor.capture(),
            any(),
            any()
        )
        runnableCaptor.lastValue.run()

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M keep unwritten RUM events W continuous profile written {new events after snapshot}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @Forgery fakeNewLongTask: ProfilerEvent.RumLongTaskEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.dataWriter = mockDataWriter
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())
        runnableCaptor.firstValue.run()
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeNewLongTask)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.CONTINUOUS)
        )

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
    }

    @Test
    fun `M write launch event with long task events W app-launch profiling result received`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH),
            longTasks = listOf(fakeRumLongTaskEvent),
            anrEvents = emptyList(),
            vitalEvents = listOf(fakeTTID)
        )
    }

    @Test
    fun `M discard result and not write W app-launch profiling result received {quota denied}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.propagateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        val launchResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)

        // When
        callbackCaptor.firstValue.onSuccess(launchResult)

        // Then
        verify(mockDataWriter).discard(launchResult)
        verify(mockDataWriter, never()).write(any(), any(), any(), any())
    }

    @Test
    fun `M write launch event with ANR events W app-launch profiling result received`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeRumAnrEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH),
            longTasks = emptyList(),
            anrEvents = listOf(fakeRumAnrEvent),
            vitalEvents = listOf(fakeTTID)
        )
    }

    @Test
    fun `M not accumulate RUM events W profiler not running {launch profiling not active}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M clear pending RUM events W app-launch profiling failed`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)

        // When
        testedFeature.onFailure(ProfilingStartReason.APPLICATION_LAUNCH)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M not accumulate RUM events after launch window closed W RUM events after launch write`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // When
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M clear pending RUM events W app-launch profiling result written`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeRumAnrEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        assertThat(testedFeature.pendingRumEvents.pendingLongTasks).isEmpty()
        assertThat(testedFeature.pendingRumEvents.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M forward ProfilingAnrDetectedEvent to RUM W onAnrDetected() {launch profiling active}`(
        @Forgery fakeEvent: ProfilingAnrDetectedEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onAnrDetected(fakeEvent)

        // Then
        verify(mockRumFeatureScope).sendEvent(fakeEvent)
    }

    @Test
    fun `M drop ProfilingAnrDetectedEvent W onAnrDetected() {profiling inactive}`(
        @Forgery fakeEvent: ProfilingAnrDetectedEvent
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onAnrDetected(fakeEvent)

        // Then
        verify(mockRumFeatureScope, never()).sendEvent(any())
    }

    @Test
    fun `M not stop Profiling W receive illegal event`(@StringForgery fakeIllegalValue: String) {
        // When
        testedFeature.onReceive(fakeIllegalValue)

        // Then
        val argumentCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            argumentCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(argumentCaptor.firstValue.invoke())
            .isEqualTo("Profiling feature received an event of unsupported type=${String::class.java.canonicalName}.")
        verify(mockProfiler, never()).stop()
    }

    @Test
    fun `M unregister profiling callback with appContext W onStop()`() {
        // Given
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onStop()

        // Then
        verify(mockProfiler).unregisterProfilingCallback(mockContext)
    }

    @Test
    fun `M fire quota check W onContextUpdate { new session id }`(forge: Forge) {
        // Given
        val fakeNewSessionId = forge.aString()
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                FeatureContextKeys.RUM_SESSION_ID to fakeNewSessionId,
                FeatureContextKeys.RUM_SESSION_SAMPLE_RATE to 100f
            )
        )

        // Then
        verify(mockQuotaChecker).checkAsync(eq(fakeNewSessionId), any())
    }

    @Test
    fun `M stamp reason and session id W propagateQuotaResult {denied for current session}`(
        @StringForgery fakeQuotaSessionId: String
    ) {
        // Given
        val fakeProfilingContext = mutableMapOf<String, Any?>()
        whenever(
            mockSdkCore.updateFeatureContext(eq(Feature.PROFILING_FEATURE_NAME), any(), any())
        ) doAnswer {
            it.getArgument<(MutableMap<String, Any?>) -> Unit>(2).invoke(fakeProfilingContext)
        }
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession(fakeQuotaSessionId, 100f)

        // When
        testedFeature.propagateQuotaResult(QuotaResult.QUOTA_EXCEEDED)

        // Then — the decision is stamped with the current session in context and fed to the scheduler
        assertThat(fakeProfilingContext[FeatureContextKeys.PROFILING_QUOTA_REASON])
            .isEqualTo(QuotaResult.QUOTA_EXCEEDED.reason.rawValue)
        assertThat(fakeProfilingContext[FeatureContextKeys.PROFILING_QUOTA_SESSION_ID])
            .isEqualTo(fakeQuotaSessionId)
        assertThat(testedFeature.continuousProfilingScheduler?.lastQuotaResult)
            .isEqualTo(QuotaResult.QUOTA_EXCEEDED)
    }

    @Test
    fun `M clear reason and session id W propagateQuotaResult {allowed for current session}`(
        @StringForgery fakeQuotaSessionId: String
    ) {
        // Given — a stale denial stamp from a previous session is present
        val fakeProfilingContext = mutableMapOf<String, Any?>(
            FeatureContextKeys.PROFILING_QUOTA_REASON to "stale-reason",
            FeatureContextKeys.PROFILING_QUOTA_SESSION_ID to "stale-session"
        )
        whenever(
            mockSdkCore.updateFeatureContext(eq(Feature.PROFILING_FEATURE_NAME), any(), any())
        ) doAnswer {
            it.getArgument<(MutableMap<String, Any?>) -> Unit>(2).invoke(fakeProfilingContext)
        }
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession(fakeQuotaSessionId, 100f)

        // When
        testedFeature.propagateQuotaResult(QuotaResult.FAIL_OPEN)

        // Then — both the reason and its session stamp are removed
        assertThat(fakeProfilingContext).doesNotContainKey(FeatureContextKeys.PROFILING_QUOTA_REASON)
        assertThat(fakeProfilingContext).doesNotContainKey(FeatureContextKeys.PROFILING_QUOTA_SESSION_ID)
    }

    @Test
    fun `M clear scheduler quota result W onContextUpdate {new session}`() {
        // Given — the current session was denied
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.propagateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        assertThat(testedFeature.continuousProfilingScheduler?.lastQuotaResult)
            .isEqualTo(QuotaResult.QUOTA_EXCEEDED)

        // When — a new session rolls over before its own quota check resolves
        testedFeature.dispatchRumSession("new-$fakeSessionId", 100f)

        // Then — the scheduler's decision is cleared until the new session's check resolves
        assertThat(testedFeature.continuousProfilingScheduler?.lastQuotaResult).isNull()
    }

    @Test
    fun `M not write launch event W app-launch profiling result received {quota denied}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.propagateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter, never()).write(
            profilingResult = any(),
            longTasks = any(),
            anrEvents = any(),
            vitalEvents = any()
        )
    }

    @Test
    fun `M write launch event W app-launch profiling result received {quota allowed}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.propagateQuotaResult(QuotaResult(QuotaResult.Decision.ALLOWED, QuotaReason.QUOTA_OK))
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH),
            longTasks = listOf(fakeRumLongTaskEvent),
            anrEvents = emptyList(),
            vitalEvents = listOf(fakeTTID)
        )
    }

    @Test
    fun `M buffer launch event then write W quota result arrives after app-launch result`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given — no quota decision is ever propagated (e.g. the quota check could not be
        // scheduled). The launch event must still be written (fail open) rather than stalled.
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(mockContext),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then — nothing is written while the quota decision is still pending
        verify(mockDataWriter, never()).write(
            profilingResult = any(),
            longTasks = any(),
            anrEvents = any(),
            vitalEvents = any()
        )

        // When — the quota decision finally lands
        testedFeature.simulateQuotaAllowed()

        // Then — the buffered launch event is now written
        verify(mockDataWriter).write(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH),
            longTasks = listOf(fakeRumLongTaskEvent),
            anrEvents = emptyList(),
            vitalEvents = listOf(fakeTTID)
        )
    }

    // Simulates the asynchronous quota decision landing (in production this is driven by the quota
    // checker's HTTP callback). Launch profiling is held until this arrives, so tests that expect a
    // launch write or a launch->continuous transition must call this before the APPLICATION_LAUNCH
    // result is delivered.
    private fun ProfilingFeature.simulateQuotaAllowed() {
        propagateQuotaResult(QuotaResult(QuotaResult.Decision.ALLOWED, QuotaReason.QUOTA_OK))
    }

    private fun ProfilingFeature.dispatchRumSession(sessionId: String, sampleRate: Float) {
        onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                FeatureContextKeys.RUM_SESSION_ID to sessionId,
                FeatureContextKeys.RUM_SESSION_SAMPLE_RATE to sampleRate
            )
        )
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
