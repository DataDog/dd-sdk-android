/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.content.SharedPreferences
import android.os.ProfilingManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.rum.RumSessionRenewedEvent
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerCallback
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingRequestFactory
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.ProfilingWriter
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
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
    private lateinit var mockProfilingFeatureScope: FeatureScope

    @Mock
    private lateinit var mockDataWriter: ProfilingWriter

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockSharedPreferencesStorage: SharedPreferencesStorage

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Forgery
    private lateinit var fakeConfiguration: ProfilingConfiguration

    @StringForgery
    private lateinit var fakeSessionId: String

    @Forgery
    private lateinit var fakeTTID: ProfilerEvent.TTID

    @Forgery
    private lateinit var fakeRumLongTaskEvent: ProfilerEvent.RumLongTaskEvent

    @Forgery
    private lateinit var fakeRumAnrEvent: ProfilerEvent.RumAnrEvent

    @StringForgery
    private lateinit var fakeInstanceName: String

    private val fakeAllSampledConfiguration = ProfilingConfiguration(
        customEndpointUrl = null,
        applicationLaunchSampleRate = 100f,
        continuousSampleRate = 100f
    )

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeInstanceName
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockProfilingExecutor
        whenever(mockProfiler.scheduledExecutorService) doReturn mockSchedulerExecutor
        whenever(mockContext.getSystemService(ProfilingManager::class.java)) doReturn (mockService)
        whenever(mockContext.getSharedPreferences(any(), any())) doReturn mockSharedPreferences
        whenever(mockSharedPreferences.edit()) doReturn mockEditor
        whenever(mockEditor.putBoolean(any(), any())) doReturn mockEditor
        whenever(mockEditor.putInt(any(), any())) doReturn mockEditor
        whenever(mockEditor.putString(any(), any())) doReturn mockEditor
        whenever(mockEditor.putStringSet(any(), any())) doReturn mockEditor
        whenever(mockEditor.putFloat(any(), any())) doReturn mockEditor
        testedFeature = ProfilingFeature(mockSdkCore, fakeConfiguration, mockProfiler)
        ProfilingStorage.sharedPreferencesStorage = mockSharedPreferencesStorage
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
    fun `M set Profiling sample rate W initialize {sample rate not exists}()`() {
        // Given
        whenever(
            mockSharedPreferencesStorage
                .getFloat("dd_profiling_sample_rate", -1f)
        ) doReturn (-1f)

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockSharedPreferencesStorage).putFloat(
            "dd_profiling_sample_rate",
            fakeConfiguration.applicationLaunchSampleRate
        )
    }

    @Test
    fun `M not set Profiling sample rate W initialize() {smaller sample rate exists}`() {
        // Given
        whenever(
            mockSharedPreferencesStorage
                .getFloat("dd_profiling_sample_rate", -1f)
        ) doReturn fakeConfiguration.applicationLaunchSampleRate - 1f

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockSharedPreferencesStorage, never()).putFloat(
            "dd_profiling_sample_rate",
            fakeConfiguration.applicationLaunchSampleRate
        )
    }

    @Test
    fun `M set Profiling sample rate W initialize() {bigger sample rate exists}`() {
        whenever(
            mockSharedPreferencesStorage.getFloat("dd_profiling_sample_rate", -1f)
        ) doReturn fakeConfiguration.applicationLaunchSampleRate + 1f

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        // Since the existing value was higher, it should be updated to the configuration value
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
        verify(mockProfiler).stop(fakeInstanceName)
    }

    @Test
    fun `M not stop Profiling W receive TTID event {continuous enabled, profiler running}`() {
        // Given — continuous sample rate = 100% so it is always sampled in
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
        testedFeature.onInitialize(mockContext)

        // When
        testedFeature.onReceive(fakeTTID)

        // Then — scheduler takes over, profiler is NOT stopped here
        verify(mockProfiler, never()).stop(fakeInstanceName)
    }

    @Test
    fun `M start continuous cycle W profiler result received {TTID session unsampled}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(fakeInstanceName),
            callbackCaptor.capture()
        )

        testedFeature.onReceive(
            RumSessionRenewedEvent(
                sessionId = "session-id",
                sessionSampled = true
            )
        )

        testedFeature.onReceive(ProfilerEvent.TTIDNotTracked)

        val runnableCaptor = argumentCaptor<Runnable>()

        // When
        callbackCaptor.firstValue.onSuccess(
            PerfettoResult(
                start = 0L,
                end = 1000L,
                tag = ProfilingStartReason.APPLICATION_LAUNCH.value,
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
            sdkInstanceNames = any(),
            durationMs = any()
        )
    }

    @Test
    fun `M start continuous cycle W profiler failure received {APPLICATION_LAUNCH tag}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(fakeInstanceName),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(
            RumSessionRenewedEvent(sessionId = fakeSessionId, sessionSampled = true)
        )
        testedFeature.onReceive(ProfilerEvent.TTIDNotTracked)

        val runnableCaptor = argumentCaptor<Runnable>()

        // When
        callbackCaptor.firstValue.onFailure(ProfilingStartReason.APPLICATION_LAUNCH.value)

        verify(mockSchedulerExecutor).schedule(runnableCaptor.capture(), any(), any())
        runnableCaptor.firstValue.run()

        // Then
        verify(mockProfiler).start(
            appContext = eq(mockContext),
            startReason = eq(ProfilingStartReason.CONTINUOUS),
            additionalAttributes = any(),
            sdkInstanceNames = any(),
            durationMs = any()
        )
    }

    @Test
    fun `M not start continuous cycle W profiler failure received {CONTINUOUS tag}`() {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn false
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(fakeInstanceName),
            callbackCaptor.capture()
        )

        // When
        callbackCaptor.firstValue.onFailure(ProfilingStartReason.CONTINUOUS.value)

        // Then
        verify(mockProfiler, never()).start(
            appContext = any(),
            startReason = any(),
            additionalAttributes = any(),
            sdkInstanceNames = any(),
            durationMs = any()
        )
    }

    @Test
    fun `M skip writing W continuous profiling result received {no RUM events}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        testedFeature.dataWriter = mockDataWriter
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingFeatureScope
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        verify(mockProfiler).registerProfilingCallback(
            eq(fakeInstanceName),
            callbackCaptor.capture()
        )
        testedFeature.onReceive(fakeTTID)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(tag = ProfilingStartReason.CONTINUOUS.value)
        )

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M write event W continuous profiling result received {RUM long task events present}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(fakeInstanceName),
            callbackCaptor.capture()
        )
        // Open the continuous accumulation window
        testedFeature.onReceive(RumSessionRenewedEvent(fakeSessionId, sessionSampled = true))
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(tag = ProfilingStartReason.APPLICATION_LAUNCH.value)
        )
        // Run the cooldown runnable to open the active window (sets isActive = true)
        val cooldownRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(cooldownRunnableCaptor.capture(), any(), any())
        cooldownRunnableCaptor.firstValue.run()
        testedFeature.onReceive(fakeRumLongTaskEvent)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(tag = ProfilingStartReason.CONTINUOUS.value)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = eq(fakePerfettoResult.copy(tag = ProfilingStartReason.CONTINUOUS.value)),
            longTasks = eq(listOf(fakeRumLongTaskEvent)),
            anrEvents = eq(emptyList())
        )
    }

    @Test
    fun `M write event W continuous profiling result received {RUM ANR events present}`(
        @Forgery fakePerfettoResult: PerfettoResult
    ) {
        // Given
        testedFeature = ProfilingFeature(mockSdkCore, fakeAllSampledConfiguration, mockProfiler)
        whenever(mockProfiler.isRunning(fakeInstanceName)) doReturn true
        val callbackCaptor = argumentCaptor<ProfilerCallback>()
        testedFeature.onInitialize(mockContext)
        testedFeature.dataWriter = mockDataWriter
        verify(mockProfiler).registerProfilingCallback(
            eq(fakeInstanceName),
            callbackCaptor.capture()
        )
        // Open the continuous accumulation window
        testedFeature.onReceive(RumSessionRenewedEvent(fakeSessionId, sessionSampled = true))
        testedFeature.onReceive(fakeTTID)
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(tag = ProfilingStartReason.APPLICATION_LAUNCH.value)
        )
        // Run the cooldown runnable to open the active window (sets isActive = true)
        val cooldownRunnableCaptor = argumentCaptor<Runnable>()
        verify(mockSchedulerExecutor).schedule(cooldownRunnableCaptor.capture(), any(), any())
        cooldownRunnableCaptor.firstValue.run()
        testedFeature.onReceive(fakeRumAnrEvent)

        // When
        callbackCaptor.firstValue.onSuccess(
            fakePerfettoResult.copy(tag = ProfilingStartReason.CONTINUOUS.value)
        )

        // Then
        verify(mockDataWriter).write(
            profilingResult = eq(fakePerfettoResult.copy(tag = ProfilingStartReason.CONTINUOUS.value)),
            longTasks = eq(emptyList()),
            anrEvents = eq(listOf(fakeRumAnrEvent))
        )
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
        verify(mockProfiler, never()).stop(fakeInstanceName)
    }
}
