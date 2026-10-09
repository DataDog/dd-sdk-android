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
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.perfetto.ProfileType
import com.datadog.android.profiling.internal.quota.NoOpQuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaReason
import com.datadog.android.profiling.internal.quota.QuotaResult
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetryEvent
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import com.datadog.android.profiling.internal.trigger.NoOpPendingTriggerProfiles
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles
import com.datadog.android.profiling.internal.trigger.ProfilingTriggerRegistrar
import com.datadog.android.profiling.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
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
    private lateinit var mockTriggerRegistrar: ProfilingTriggerRegistrar

    @Mock
    private lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    private lateinit var mockPendingTriggerProfiles: PendingTriggerProfiles

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
        continuousSampleRate = 100f,
        anrTriggerEnabled = true
    )

    private val fakeAnrTriggerOnlyConfiguration = ProfilingConfiguration(
        customEndpointUrl = null,
        applicationLaunchSampleRate = 0f,
        continuousSampleRate = 0f,
        anrTriggerEnabled = true
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
    fun `M query the package manager once W start() then initialize()`() {
        // Given a profiler already started by the content provider, before initialization
        val profiler = PerfettoProfiler(
            timeProvider = MutableTimeProvider.create(mockTimeProvider),
            scheduledExecutorService = mockSchedulerExecutor,
            profilingTelemetry = ProfilingTelemetry(),
            triggerRegistrar = mockTriggerRegistrar,
            buildSdkVersionProvider = mockBuildSdkVersionProvider
        )
        val feature = ProfilingFeature(
            sdkCore = mockSdkCore,
            configuration = fakeConfiguration.copy(continuousSampleRate = 0f),
            profiler = profiler
        )
        profiler.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

        // When
        feature.onInitialize(mockContext)

        // Then
        verify(mockPackageManager, times(1))
            .getPackageInfo("com.google.android.profiling", PackageManager.MATCH_APEX)
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
            triggerRegistrar = mockTriggerRegistrar,
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
    fun `M expose continuous profiling sample rate W initialize()`() {
        // Given
        val context = mutableMapOf<String, Any?>()
        whenever(mockSdkCore.updateFeatureContext(eq(Feature.PROFILING_FEATURE_NAME), any(), any())) doAnswer {
            it.getArgument<(MutableMap<String, Any?>) -> Unit>(2).invoke(context)
        }

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(context[FeatureContextKeys.PROFILING_SAMPLE_RATE])
            .isEqualTo(fakeConfiguration.continuousSampleRate)
    }

    @Test
    fun `M expose application launch sample rate & ANR flag W initialize()`() {
        // Given
        val context = mutableMapOf<String, Any?>()
        whenever(mockSdkCore.updateFeatureContext(eq(Feature.PROFILING_FEATURE_NAME), any(), any())) doAnswer {
            it.getArgument<(MutableMap<String, Any?>) -> Unit>(2).invoke(context)
        }

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(context[FeatureContextKeys.PROFILING_APPLICATION_LAUNCH_SAMPLE_RATE])
            .isEqualTo(fakeConfiguration.applicationLaunchSampleRate)
        assertThat(context[FeatureContextKeys.PROFILING_ANR_ENABLED])
            .isEqualTo(fakeConfiguration.anrTriggerEnabled)
    }

    @Test
    fun `M stop Profiling W receive TTID event {continuous disabled}`() {
        // Given — continuous disabled, profiler not running (no active launch session)
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = 0f,
                anrTriggerEnabled = true
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
        val expectedDecision = DeterministicSampler(
            SessionSamplingIdProvider::provideId,
            expectedEffectiveRate
        ).sample(realSessionId)
        testedFeature = ProfilingFeature(
            mockSdkCore,
            ProfilingConfiguration(
                customEndpointUrl = null,
                applicationLaunchSampleRate = 100f,
                continuousSampleRate = fakeContinuousRate,
                anrTriggerEnabled = true
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
    fun `M propagate ANR trigger enabled flag W onInitialize`(
        @BoolForgery fakeAnrTriggerEnabled: Boolean
    ) {
        // Given
        val config = fakeConfiguration.copy(anrTriggerEnabled = fakeAnrTriggerEnabled)
        testedFeature = ProfilingFeature(mockSdkCore, config, mockProfiler)

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockProfiler).setAnrTriggerEnabled(fakeAnrTriggerEnabled)
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
    fun `M ignore context update W onContextUpdate() {application-only RUM context}`(
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf("application_id" to fakeApplicationId)
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
    fun `M stop and reset pendingTriggerProfiles W onStop()`() {
        // Given
        testedFeature.onInitialize(mockContext)
        testedFeature.pendingTriggerProfiles = mockPendingTriggerProfiles

        // When
        testedFeature.onStop()

        // Then
        verify(mockPendingTriggerProfiles).stop()
        assertThat(testedFeature.pendingTriggerProfiles).isInstanceOf(NoOpPendingTriggerProfiles::class.java)
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
                resultFilePath = "/fake/path",
                profileTypes = listOf(ProfileType.STACK_SAMPLING)
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
        verify(mockDataWriter).writeManualProfile(
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
        verify(mockDataWriter).writeManualProfile(
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
        verify(mockDataWriter).writeManualProfile(
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
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1L,
                resultFilePath = "/fake",
                profileTypes = listOf(ProfileType.STACK_SAMPLING)
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
                continuousSampleRate = 0f,
                anrTriggerEnabled = true
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
    fun `M forward gating event to pendingTriggerProfiles W onReceive() {RumAnrEvent}`() {
        // Given
        testedFeature.onInitialize(mockContext)
        testedFeature.pendingTriggerProfiles = mockPendingTriggerProfiles

        // When
        testedFeature.onReceive(fakeRumAnrEvent)

        // Then
        verify(mockPendingTriggerProfiles).setRumGatingEvent(fakeRumAnrEvent)
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
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1L,
                resultFilePath = "/fake",
                profileTypes = listOf(ProfileType.STACK_SAMPLING)
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
                continuousSampleRate = 0f,
                anrTriggerEnabled = true
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
                continuousSampleRate = 0f,
                anrTriggerEnabled = true
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
                continuousSampleRate = 0f,
                anrTriggerEnabled = true
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
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                end = 1L,
                resultFilePath = "/fake",
                profileTypes = listOf(ProfileType.STACK_SAMPLING)
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
                resultFilePath = "/fake",
                profileTypes = listOf(ProfileType.STACK_SAMPLING)
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter).writeManualProfile(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH),
            longTasks = listOf(fakeRumLongTaskEvent),
            anrEvents = emptyList(),
            vitalEvents = listOf(fakeTTID)
        )
    }

    @Test
    fun `M delete result file and not write W app-launch profiling result received {quota denied}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @TempDir fakeTempDir: File
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        val traceFile = File(fakeTempDir, "launch_trace.perfetto-stack-sample").apply { writeText("trace") }
        val launchResult = fakePerfettoResult.copy(
            resultFilePath = traceFile.absolutePath,
            startReason = ProfilingStartReason.APPLICATION_LAUNCH
        )

        // When
        callbackCaptor.firstValue.onSuccess(launchResult)

        // Then
        assertThat(traceFile.exists()).isFalse
        verifyNoInteractions(mockDataWriter)
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.onReceive(fakeRumAnrEvent)
        testedFeature.onReceive(fakeTTID)
        testedFeature.simulateQuotaAllowed()

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter).writeManualProfile(
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
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
        @Forgery fakeEvent: ProfilingAnrDetectedEvent,
        @Forgery fakeResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onAnrDetected(fakeEvent, fakeResult)

        // Then
        verify(mockRumFeatureScope).sendEvent(fakeEvent)
    }

    @Test
    fun `M forward ProfilingAnrDetectedEvent to RUM W onAnrDetected() {result startReason is ANR}`(
        @Forgery fakeEvent: ProfilingAnrDetectedEvent,
        @Forgery fakeResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        val fakeAnrResult = fakeResult.copy(startReason = ProfilingStartReason.ANR)
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onAnrDetected(fakeEvent, fakeAnrResult)

        // Then
        verify(mockRumFeatureScope).sendEvent(fakeEvent)
    }

    @Test
    fun `M buffer ANR profiling result and delete file on expiry W onAnrDetected() {no matching gating event}`(
        @Forgery fakeEvent: ProfilingAnrDetectedEvent,
        @Forgery fakeResult: PerfettoResult,
        @TempDir fakeTempDir: File
    ) {
        // Given
        val traceFile = File(fakeTempDir, "anr_trace.proto").apply { writeText("trace") }
        val bufferedResult = fakeResult.copy(resultFilePath = traceFile.absolutePath)
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter

        // When
        testedFeature.onAnrDetected(fakeEvent, bufferedResult)

        // Then
        verify(mockRumFeatureScope).sendEvent(fakeEvent)

        // When
        val runnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor, atLeastOnce()).schedule(
            runnableCaptor.capture(),
            any(),
            any()
        )
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn
            bufferedResult.start + PendingTriggerProfiles.EXPIRY_TIMEOUT_MS + 1L
        runnableCaptor.lastValue.run()

        // Then
        assertThat(traceFile.exists()).isFalse
    }

    @Test
    fun `M write trigger profile W onMatch {ANR, quota allowed}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = fakeRumAnrEvent.rumContext.sessionId)
        testedFeature.simulateQuotaAllowed()
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)

        // When
        matchAnrTriggerProfile(anrResult)

        // Then
        verify(mockDataWriter).writeTriggerProfile(
            perfettoResult = anrResult,
            rumErrorId = fakeRumAnrEvent.id,
            rumContext = fakeRumAnrEvent.rumContext
        )
    }

    @Test
    fun `M check quota then write trigger profile W onMatch {ANR, quota not yet resolved}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = fakeRumAnrEvent.rumContext.sessionId)
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)

        // When
        matchAnrTriggerProfile(anrResult)

        // Then
        verify(mockQuotaChecker).checkAsync(eq(fakeRumAnrEvent.rumContext.sessionId), any())
        verifyNoInteractions(mockDataWriter)

        // When
        testedFeature.simulateQuotaAllowed()

        // Then
        verify(mockDataWriter).writeTriggerProfile(
            perfettoResult = anrResult,
            rumErrorId = fakeRumAnrEvent.id,
            rumContext = fakeRumAnrEvent.rumContext
        )
    }

    @Test
    fun `M delete trigger profile file W quota denied after onMatch {ANR}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = fakeRumAnrEvent.rumContext.sessionId)
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)
        matchAnrTriggerProfile(anrResult)

        // When
        testedFeature.simulateQuotaResult(QuotaResult.QUOTA_EXCEEDED)

        // Then
        assertThat(File(anrResult.resultFilePath)).doesNotExist()
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M delete trigger profile file W session renewed before quota decision {ANR}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeNewSessionId: String,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = fakeRumAnrEvent.rumContext.sessionId)
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)
        matchAnrTriggerProfile(anrResult)

        // When
        testedFeature.dispatchRumSession("new-$fakeNewSessionId", 100f)
        testedFeature.simulateQuotaAllowed()

        // Then
        assertThat(File(anrResult.resultFilePath)).doesNotExist()
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M not check quota for past session W onMatch {session renewed before context is resolved}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeNewSessionId: String,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = fakeRumAnrEvent.rumContext.sessionId)
        val pendingContextCallbacks = deferWithContextCallbacks()
        matchAnrTriggerProfile(fakeAnrResult(fakePerfettoResult, fakeTempDir))
        testedFeature.dispatchRumSession("new-$fakeNewSessionId", 100f)

        // When
        pendingContextCallbacks.forEach { it.invoke(fakeDatadogContext) }

        // Then
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
    }

    @Test
    fun `M not check quota for past session W onContextUpdate {session renewed before context is resolved}`(
        @StringForgery fakeSessionId: String,
        @StringForgery fakeNewSessionId: String
    ) {
        // Given
        testedFeature = ProfilingFeature(
            mockSdkCore,
            fakeAllSampledConfiguration.copy(anrTriggerEnabled = false),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker
        val pendingContextCallbacks = deferWithContextCallbacks()
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.dispatchRumSession("new-$fakeNewSessionId", 0f)

        // When
        pendingContextCallbacks.forEach { it.invoke(fakeDatadogContext) }

        // Then
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
    }

    @Test
    fun `M discard quota result W propagateQuotaResult {result for past session}`(
        @StringForgery fakePastSessionId: String
    ) {
        // Given
        val fakeProfilingContext = mutableMapOf<String, Any?>()
        whenever(
            mockSdkCore.updateFeatureContext(eq(Feature.PROFILING_FEATURE_NAME), any(), any())
        ) doAnswer {
            it.getArgument<(MutableMap<String, Any?>) -> Unit>(2).invoke(fakeProfilingContext)
        }
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession("past-$fakePastSessionId", 100f)
        testedFeature.dispatchRumSession(fakeSessionId, 100f)

        // When
        testedFeature.propagateQuotaResult("past-$fakePastSessionId", QuotaResult.QUOTA_EXCEEDED)

        // Then
        assertThat(testedFeature.continuousProfilingScheduler?.lastQuotaResult).isNull()
        assertThat(fakeProfilingContext).doesNotContainKey(FeatureContextKeys.PROFILING_QUOTA_REASON)
    }

    @Test
    fun `M not resolve trigger profile W propagateQuotaResult {result for past session}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakePastSessionId: String,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = "past-$fakePastSessionId")
        testedFeature.dispatchRumSession(fakeRumAnrEvent.rumContext.sessionId, 100f)
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)
        matchAnrTriggerProfile(anrResult)

        // When
        testedFeature.propagateQuotaResult("past-$fakePastSessionId", QuotaResult.QUOTA_EXCEEDED)

        // Then
        assertThat(File(anrResult.resultFilePath)).exists()
        verifyNoInteractions(mockDataWriter)

        // When
        testedFeature.simulateQuotaAllowed()

        // Then
        verify(mockDataWriter).writeTriggerProfile(
            perfettoResult = anrResult,
            rumErrorId = fakeRumAnrEvent.id,
            rumContext = fakeRumAnrEvent.rumContext
        )
    }

    @Test
    fun `M delete trigger profile file W onMatch {ANR from past session, quota not yet resolved}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeCurrentSessionId: String,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = "current-$fakeCurrentSessionId")
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)

        // When
        matchAnrTriggerProfile(anrResult)

        // Then
        assertThat(File(anrResult.resultFilePath)).doesNotExist()
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M delete trigger profile file W onMatch {ANR, quota denied}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = fakeRumAnrEvent.rumContext.sessionId)
        testedFeature.simulateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)

        // When
        matchAnrTriggerProfile(anrResult)

        // Then
        assertThat(File(anrResult.resultFilePath)).doesNotExist()
        verifyNoInteractions(mockDataWriter)
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.DEBUG,
            target = InternalLogger.Target.USER,
            message = ProfilingFeature.LOG_TRIGGER_PROFILING_DROPPED_QUOTA_DENIED.format(
                Locale.US,
                QuotaResult.QUOTA_EXCEEDED.reason.rawValue
            ),
            mode = atLeastOnce()
        )
    }

    @Test
    fun `M delete trigger profile file W onMatch {ANR from past session, current session quota allowed}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeCurrentSessionId: String,
        @TempDir fakeTempDir: File
    ) {
        // Given
        initializeAnrTriggerOnlyFeature(currentSessionId = "current-$fakeCurrentSessionId")
        testedFeature.simulateQuotaAllowed()
        val anrResult = fakeAnrResult(fakePerfettoResult, fakeTempDir)

        // When
        matchAnrTriggerProfile(anrResult)

        // Then
        assertThat(File(anrResult.resultFilePath)).doesNotExist()
        verifyNoInteractions(mockDataWriter)
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
    fun `M fire quota check W onContextUpdate {new session sampled for continuous profiling}`(
        forge: Forge
    ) {
        // Given
        val fakeNewSessionId = forge.aString()
        testedFeature = ProfilingFeature(
            mockSdkCore,
            fakeAllSampledConfiguration.copy(anrTriggerEnabled = false),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.dispatchRumSession(fakeNewSessionId, 100f)

        // Then
        assertThat(testedFeature.continuousProfilingScheduler?.currentSessionSampled).isTrue()
        inOrder(mockQuotaChecker) {
            verify(mockQuotaChecker).reset()
            verify(mockQuotaChecker).checkAsync(eq(fakeNewSessionId), any())
        }
    }

    @Test
    fun `M fire quota check W onContextUpdate {launch profiling active, continuous sampled out}`(
        forge: Forge
    ) {
        // Given
        val fakeNewSessionId = forge.aString()
        testedFeature = ProfilingFeature(
            mockSdkCore,
            fakeAllSampledConfiguration.copy(continuousSampleRate = 0f, anrTriggerEnabled = false),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.dispatchRumSession(fakeNewSessionId, 100f)

        // Then
        assertThat(testedFeature.continuousProfilingScheduler?.currentSessionSampled).isFalse()
        verify(mockQuotaChecker).checkAsync(eq(fakeNewSessionId), any())
    }

    @Test
    fun `M not fire quota check W onContextUpdate {ANR trigger enabled, session not sampled}`(
        forge: Forge
    ) {
        // Given
        val fakeNewSessionId = forge.aString()
        testedFeature = ProfilingFeature(mockSdkCore, fakeAnrTriggerOnlyConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.dispatchRumSession(fakeNewSessionId, 100f)

        // Then
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
    }

    @Test
    fun `M not fire quota check W onContextUpdate {RUM session not tracked}`(
        @StringForgery fakeNewSessionId: String,
        @StringForgery fakeSessionState: String
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.dispatchRumSession(fakeNewSessionId, 100f, sessionState = fakeSessionState)

        // Then
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
        verify(mockQuotaChecker).reset()
    }

    @Test
    fun `M not fire quota check W onContextUpdate {RUM session state missing}`(
        @StringForgery fakeNewSessionId: String
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)
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
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
    }

    @Test
    fun `M not fire quota check W onContextUpdate {no launch, continuous sampled out, ANR disabled}`(
        forge: Forge
    ) {
        // Given
        val fakeNewSessionId = forge.aString()
        testedFeature = ProfilingFeature(
            mockSdkCore,
            fakeAllSampledConfiguration.copy(
                continuousSampleRate = 0f,
                anrTriggerEnabled = false
            ),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.quotaChecker = mockQuotaChecker

        // When
        testedFeature.dispatchRumSession(fakeNewSessionId, 100f)

        // Then
        verify(mockQuotaChecker, never()).checkAsync(any(), any())
        verify(mockQuotaChecker).reset()
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
        testedFeature.simulateQuotaResult(QuotaResult.QUOTA_EXCEEDED)

        // Then
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
        // Given
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
        testedFeature.simulateQuotaResult(QuotaResult.FAIL_OPEN)

        // Then
        assertThat(fakeProfilingContext).doesNotContainKey(FeatureContextKeys.PROFILING_QUOTA_REASON)
        assertThat(fakeProfilingContext).doesNotContainKey(FeatureContextKeys.PROFILING_QUOTA_SESSION_ID)
    }

    @Test
    fun `M clear scheduler quota result W onContextUpdate {new session}`() {
        // Given
        testedFeature.onInitialize(mockContext)
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        assertThat(testedFeature.continuousProfilingScheduler?.lastQuotaResult)
            .isEqualTo(QuotaResult.QUOTA_EXCEEDED)

        // When
        testedFeature.dispatchRumSession("new-$fakeSessionId", 100f)

        // Then
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaResult(QuotaResult.QUOTA_EXCEEDED)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verifyNoInteractions(mockDataWriter)
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaResult(QuotaResult(QuotaResult.Decision.ALLOWED, QuotaReason.QUOTA_OK))
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verify(mockDataWriter).writeManualProfile(
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verifyNoInteractions(mockDataWriter)

        // When
        testedFeature.simulateQuotaAllowed()

        // Then
        verify(mockDataWriter).writeManualProfile(
            profilingResult = fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH),
            longTasks = listOf(fakeRumLongTaskEvent),
            anrEvents = emptyList(),
            vitalEvents = listOf(fakeTTID)
        )
    }

    @Test
    fun `M drop launch event W app-launch profiling result received {RUM session not tracked}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeNotTrackedSessionState: String
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
        testedFeature.dispatchRumSession(fakeSessionId, 100f, sessionState = fakeNotTrackedSessionState)
        testedFeature.onReceive(fakeRumLongTaskEvent)
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verifyNoInteractions(mockDataWriter)
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.DEBUG,
            target = InternalLogger.Target.USER,
            message = ProfilingFeature.LOG_LAUNCH_PROFILING_DROPPED_SESSION_NOT_TRACKED,
            mode = atLeastOnce()
        )
    }

    @Test
    fun `M write launch event W later RUM session not tracked {launch session allowed}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeNotTrackedSessionState: String
    ) {
        // Given
        initializeLaunchProfilingFeature()
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        verify(mockProfiler).registerProfilingCallback(eq(mockContext), callbackCaptor.capture())
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.simulateQuotaAllowed()
        testedFeature.dispatchRumSession("new-$fakeSessionId", 100f, sessionState = fakeNotTrackedSessionState)
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then — judged by its own session, not by the later untracked one
        verify(mockDataWriter).writeManualProfile(
            profilingResult = eq(fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)),
            longTasks = any(),
            anrEvents = any(),
            vitalEvents = any()
        )
    }

    @Test
    fun `M drop launch event W launch session ends before quota decision`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        initializeLaunchProfilingFeature()
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        verify(mockProfiler).registerProfilingCallback(eq(mockContext), callbackCaptor.capture())
        testedFeature.dispatchRumSession(fakeSessionId, 100f)
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // When
        testedFeature.dispatchRumSession("new-$fakeSessionId", 100f)
        testedFeature.simulateQuotaAllowed()

        // Then
        verifyNoInteractions(mockDataWriter)
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.DEBUG,
            target = InternalLogger.Target.USER,
            message = ProfilingFeature.LOG_LAUNCH_PROFILING_DROPPED_SESSION_ENDED,
            mode = atLeastOnce()
        )
    }

    @Test
    fun `M drop launch event W launch session not tracked {later session tracked and allowed}`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeNotTrackedSessionState: String
    ) {
        // Given
        initializeLaunchProfilingFeature()
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        verify(mockProfiler).registerProfilingCallback(eq(mockContext), callbackCaptor.capture())
        testedFeature.dispatchRumSession(fakeSessionId, 100f, sessionState = fakeNotTrackedSessionState)
        testedFeature.dispatchRumSession("new-$fakeSessionId", 100f)
        testedFeature.simulateQuotaAllowed()
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M not fire quota check W onContextUpdate {launch profile pending, not the launch session}`(
        @StringForgery fakeNewSessionId: String
    ) {
        // Given
        initializeLaunchProfilingFeature()
        testedFeature.dispatchRumSession(fakeSessionId, 100f)

        // When
        testedFeature.dispatchRumSession("new-$fakeNewSessionId", 100f)

        // Then
        verify(mockQuotaChecker).checkAsync(eq(fakeSessionId), any())
        verify(mockQuotaChecker, never()).checkAsync(eq("new-$fakeNewSessionId"), any())
    }

    @Test
    fun `M drop buffered launch event W untracked RUM session arrives after app-launch result`(
        @Forgery fakePerfettoResult: PerfettoResult,
        @StringForgery fakeNotTrackedSessionState: String
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
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(startReason = ProfilingStartReason.APPLICATION_LAUNCH)
        )

        // When
        testedFeature.dispatchRumSession(fakeSessionId, 100f, sessionState = fakeNotTrackedSessionState)
        testedFeature.simulateQuotaAllowed()

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    private fun initializeAnrTriggerOnlyFeature(currentSessionId: String) {
        testedFeature = ProfilingFeature(mockSdkCore, fakeAnrTriggerOnlyConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning()) doReturn false
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        testedFeature.quotaChecker = mockQuotaChecker
        testedFeature.dispatchRumSession(currentSessionId, 100f)
    }

    private fun fakeAnrResult(fakePerfettoResult: PerfettoResult, dir: File): PerfettoResult {
        val traceFile = File(dir, "anr_trace.perfetto-stack-sample").apply { writeText("trace") }
        return fakePerfettoResult.copy(
            resultFilePath = traceFile.absolutePath,
            startReason = ProfilingStartReason.ANR
        )
    }

    // Launch profiling active, continuous profiling disabled.
    private fun initializeLaunchProfilingFeature() {
        testedFeature = ProfilingFeature(
            mockSdkCore,
            fakeAllSampledConfiguration.copy(continuousSampleRate = 0f, anrTriggerEnabled = false),
            mockProfiler
        )
        whenever(mockProfiler.isRunning()) doReturn true
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        testedFeature.quotaChecker = mockQuotaChecker
    }

    // Collects withContext callbacks instead of running them, as the production context executor
    // runs them asynchronously.
    private fun deferWithContextCallbacks(): List<(DatadogContext) -> Unit> {
        val callbacks = mutableListOf<(DatadogContext) -> Unit>()
        whenever(mockProfilingFeatureScope.withContext(any(), any())) doAnswer {
            callbacks.add(it.getArgument(1))
            Unit
        }
        return callbacks
    }

    private fun matchAnrTriggerProfile(anrResult: PerfettoResult) {
        testedFeature.pendingTriggerProfiles.setRumGatingEvent(fakeRumAnrEvent)
        testedFeature.pendingTriggerProfiles.setProfilingResult(anrResult)
    }

    // Simulates the asynchronous quota decision landing (in production this is driven by the quota
    // checker's HTTP callback). Launch profiling is held until this arrives, so tests that expect a
    // launch write or a launch->continuous transition must call this before the APPLICATION_LAUNCH
    // result is delivered.
    private fun ProfilingFeature.simulateQuotaAllowed() {
        simulateQuotaResult(QuotaResult(QuotaResult.Decision.ALLOWED, QuotaReason.QUOTA_OK))
    }

    // Delivers a quota decision for the current RUM session.
    private fun ProfilingFeature.simulateQuotaResult(result: QuotaResult) {
        propagateQuotaResult(checkNotNull(lastSeenRumSessionId), result)
    }

    private fun ProfilingFeature.dispatchRumSession(
        sessionId: String,
        sampleRate: Float,
        sessionState: String = RumSessionConstants.TRACKED_SESSION_STATE
    ) {
        onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                FeatureContextKeys.RUM_SESSION_ID to sessionId,
                FeatureContextKeys.RUM_SESSION_SAMPLE_RATE to sampleRate,
                FeatureContextKeys.RUM_SESSION_STATE to sessionState
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
