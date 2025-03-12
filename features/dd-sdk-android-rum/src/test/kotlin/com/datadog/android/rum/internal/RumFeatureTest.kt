/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.JvmCrash
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.RumFeatureAssert
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.metric.SessionEndedMetricDispatcher
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor
import com.datadog.android.rum.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListener
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalReaderRunnable
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.config.MainLooperTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.datadog.tools.unit.getFieldValue
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureTest {

    private lateinit var testedFeature: RumFeature

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeConfiguration: RumFeature.Configuration

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionEndedMetricDispatcher

    @Mock
    lateinit var mockLateCrashReporter: LateCrashReporter

    @Mock
    lateinit var mockScheduledExecutorService: ScheduledExecutorService

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn mockScheduledExecutorService

        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
    }

    @Test
    fun `M initialize persistence strategy W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(RumDataWriter::class.java)
    }

    @Test
    fun `M store sample rate W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.sampleRate).isEqualTo(fakeConfiguration.sampleRate)
    }

    @Test
    fun `M set sample rate to 100 W initialize() {developer mode enabled}`() {
        // Given
        whenever(mockSdkCore.isDeveloperModeEnabled) doReturn true

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.sampleRate).isEqualTo(RumFeature.ALL_IN_SAMPLE_RATE)
        mockSdkCore.internalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumFeature.DEVELOPER_MODE_SAMPLE_RATE_CHANGED_MESSAGE
        )
    }

    @Test
    fun `M store telemetry sample rate W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.telemetrySampleRate)
            .isEqualTo(fakeConfiguration.telemetrySampleRate)
    }

    @Test
    fun `M store background tracking W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.backgroundEventTracking)
            .isEqualTo(fakeConfiguration.backgroundEventTracking)
    }

    @Test
    fun `M store and register viewTrackingStrategy W initialize()`(
        @Forgery fakeApplicationId: UUID
    ) {
        // Given
        val mockViewTrackingStrategy = mock<ViewTrackingStrategy>()
        fakeConfiguration =
            fakeConfiguration.copy(viewTrackingStrategy = mockViewTrackingStrategy)
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.viewTrackingStrategy).isEqualTo(mockViewTrackingStrategy)
        verify(mockViewTrackingStrategy).register(mockSdkCore, appContext.mockInstance)
    }

    @Test
    fun `M set the NoOpUserActionTrackingStrategy W initialize() {userActionTracking = false}`() {
        // Given
        fakeConfiguration =
            fakeConfiguration.copy(userActionTracking = false)
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasNoOpUserActionTrackingStrategy()
    }

    @Test
    fun `M bundle the custom attributes providers W initialize()`(
        @IntForgery(0, 10) attributesCount: Int
    ) {
        // Given
        val mockProviders = Array<ViewAttributesProvider>(attributesCount) {
            mock()
        }
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true,
            touchTargetExtraAttributesProviders = mockProviders.toList()
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasActionTargetAttributeProviders(mockProviders)
    }

    @Test
    fun `M bundle only the default providers W initialize { providers not provided }`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasDefaultActionTargetAttributeProviders()
    }

    @Test
    fun `M use the custom predicate W initialize()`() {
        // Given
        val mockInteractionPredicate: InteractionPredicate = mock()
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true,
            interactionPredicate = mockInteractionPredicate
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasInteractionPredicate(mockInteractionPredicate)
    }

    @Test
    fun `M use the NoOpInteractionPredicate W initialize() { predicate not provided }`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true,
            interactionPredicate = NoOpInteractionPredicate()
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasInteractionPredicateOfType(NoOpInteractionPredicate::class.java)
    }

    @TestTargetApi(Build.VERSION_CODES.Q)
    @Test
    fun `M build config with gestures enabled W initialize() {Android Q}`(
        @IntForgery(0, 10) attributesCount: Int
    ) {
        // Given
        val mockProviders = Array<ViewAttributesProvider>(attributesCount) {
            mock()
        }
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true,
            touchTargetExtraAttributesProviders = mockProviders.toList()
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyApi29()
            .hasActionTargetAttributeProviders(mockProviders)
    }

    @Test
    fun `M store longTaskTrackingStrategy W initialize()`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isEqualTo(fakeConfiguration.longTaskTrackingStrategy)
        verify(fakeConfiguration.longTaskTrackingStrategy!!)
            .register(mockSdkCore, appContext.mockInstance)
    }

    @Test
    fun `M use noop viewTrackingStrategy W initialize()`() {
        // Given
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration.copy(viewTrackingStrategy = null),
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `M use noop userActionTrackingStrategy W initialize()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(userActionTracking = false)
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `M use noop longTaskTrackingStrategy W initialize()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(longTaskTrackingStrategy = null)
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isInstanceOf(NoOpTrackingStrategy::class.java)
    }

    @Test
    fun `M store eventMapper W initialize()`() {
        // Given
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(RumDataWriter::class.java)
        val serializer = (testedFeature.dataWriter as RumDataWriter).eventSerializer
        assertThat(serializer).isInstanceOf(MapperSerializer::class.java)
        val eventMapper = (serializer as MapperSerializer)
            .getFieldValue<EventMapper<*>, MapperSerializer<*>>("eventMapper")
        assertThat(eventMapper).isInstanceOf(RumEventMapper::class.java)
        val rumEventMapper = eventMapper as RumEventMapper
        assertThat(rumEventMapper.actionEventMapper)
            .isSameAs(fakeConfiguration.actionEventMapper)
        assertThat(rumEventMapper.errorEventMapper)
            .isSameAs(fakeConfiguration.errorEventMapper)
        assertThat(rumEventMapper.resourceEventMapper)
            .isSameAs(fakeConfiguration.resourceEventMapper)
        assertThat(rumEventMapper.viewEventMapper)
            .isSameAs(fakeConfiguration.viewEventMapper)
        assertThat(rumEventMapper.longTaskEventMapper)
            .isSameAs(fakeConfiguration.longTaskEventMapper)
        assertThat(rumEventMapper.telemetryConfigurationMapper)
            .isSameAs(fakeConfiguration.telemetryConfigurationMapper)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `M setup vital monitors W initialize { frequency != NEVER }`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // Given
        fakeConfiguration = fakeConfiguration.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.cpuVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.jankStatsActivityLifecycleListener)
            .isInstanceOf(JankStatsActivityLifecycleListener::class.java)
        verify(appContext.mockInstance).registerActivityLifecycleCallbacks(
            testedFeature.jankStatsActivityLifecycleListener
        )
    }

    @Test
    fun `M not initialize the vital monitors W initialize { frequency = NEVER }`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.cpuVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
        assertThat(testedFeature.jankStatsActivityLifecycleListener)
            .isNull()
    }

    @Test
    fun `M call onActivityStarted W enableJankStatsTracking()`() {
        // Given
        val activity: Activity = mock()
        testedFeature = RumFeature(
            mockSdkCore,
            fakeApplicationId.toString(),
            fakeConfiguration,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )
        val mockJankStatsActivityLifecycleListener = mock<JankStatsActivityLifecycleListener>()
        testedFeature.jankStatsActivityLifecycleListener = mockJankStatsActivityLifecycleListener

        // When
        testedFeature.enableJankStatsTracking(activity)

        // Then
        verify(mockJankStatsActivityLifecycleListener).onActivityStarted(activity)
    }

    @Test
    fun `M use noop viewTrackingStrategy W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `M use noop userActionTrackingStrategy W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `M unregister strategies W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val mockActionTrackingStrategy: UserActionTrackingStrategy = mock()
        val mockViewTrackingStrategy: ViewTrackingStrategy = mock()
        val mockLongTaskTrackingStrategy: TrackingStrategy = mock()
        testedFeature.actionTrackingStrategy = mockActionTrackingStrategy
        testedFeature.viewTrackingStrategy = mockViewTrackingStrategy
        testedFeature.longTaskTrackingStrategy = mockLongTaskTrackingStrategy

        // When
        testedFeature.onStop()

        // Then
        verify(mockActionTrackingStrategy).unregister(appContext.mockInstance)
        verify(mockViewTrackingStrategy).unregister(appContext.mockInstance)
        verify(mockLongTaskTrackingStrategy).unregister(appContext.mockInstance)
    }

    @Test
    fun `M reset data writer W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpDataWriter::class.java)
    }

    @Test
    fun `M remove associated monitor W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // When
        testedFeature.onStop()

        // Then
        assertThat(GlobalRumMonitor.isRegistered(mockSdkCore)).isFalse
        assertThat(GlobalRumMonitor.get(mockSdkCore)).isInstanceOf(NoOpAdvancedRumMonitor::class.java)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `M initialize vital executor W initialize { frequency != NEVER }()`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = fakeFrequency
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        argumentCaptor<Runnable> {
            verify(mockScheduledExecutorService, times(2)).schedule(
                capture(),
                eq(fakeFrequency.periodInMs),
                eq(TimeUnit.MILLISECONDS)
            )
            allValues.forEach {
                assertThat(it).isInstanceOf(VitalReaderRunnable::class.java)
            }
        }
    }

    @Test
    fun `M not initialize vital executor W initialize { frequency = NEVER }()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `M initialize non-fatal ANR tracking  W initialize { trackNonFatalAnrs = true }()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            trackNonFatalAnrs = true
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.anrDetectorRunnable)
            .isNotNull()
    }

    @Test
    fun `M not initialize non-fatal ANR tracking  W initialize { trackNonFatalAnrs = false }()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            trackNonFatalAnrs = false
        )
        testedFeature = RumFeature(
            sdkCore = mockSdkCore,
            applicationId = fakeApplicationId.toString(),
            configuration = fakeConfiguration,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            lateCrashReporterFactory = { mockLateCrashReporter }
        )

        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        assertThat(testedFeature.anrDetectorRunnable)
            .isNull()
    }

    @Test
    fun `M shut down vital executor W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val mockVitalExecutorService: ScheduledThreadPoolExecutor = mock()
        testedFeature.vitalExecutorService = mockVitalExecutorService

        // When
        testedFeature.onStop()

        // Then
        verify(mockVitalExecutorService).shutdownNow()
    }

    @Test
    fun `M reset vital executor W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `M reset vital monitors W onStop()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
    }

    @Test
    fun `M enable RUM debugging W enableDebugging()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.enableDebugging(mockRumMonitor)

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener.get()).isNotNull
        verify(testedFeature.appContext as Application)
            .registerActivityLifecycleCallbacks(testedFeature.debugActivityLifecycleListener.get())
    }

    @Test
    fun `M enable RUM debugging only once W enableDebugging()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.enableDebugging(mockRumMonitor)
        testedFeature.enableDebugging(mockRumMonitor)

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener.get()).isNotNull
        verify(testedFeature.appContext as Application)
            .registerActivityLifecycleCallbacks(testedFeature.debugActivityLifecycleListener.get())
    }

    @Test
    fun `M enable RUM debugging W enableDebugging(){RUM feature is not yet initialized}`() {
        // When
        testedFeature.enableDebugging(mockRumMonitor)

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).hasValue(null)
    }

    @Test
    fun `M disable RUM debugging W disableDebugging()`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.enableDebugging(mockRumMonitor)
        val listener = testedFeature.debugActivityLifecycleListener.get()

        // When
        testedFeature.disableDebugging()

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).hasValue(null)
        verify(testedFeature.appContext as Application)
            .unregisterActivityLifecycleCallbacks(listener)
    }

    // region FeatureEventReceiver#onReceive

    @Test
    fun `M log dev warning and do nothing else W onReceive() { unknown type }`() {
        // When
        testedFeature.onReceive(Any())

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumFeature.UNSUPPORTED_EVENT_TYPE.format(
                Locale.US,
                Any()::class.java.canonicalName
            )
        )

        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M log dev warning and do nothing else W onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        val event = mapOf(
            "type" to forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(
                Locale.US,
                event["type"]
            )
        )

        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    // region FeatureEventReceiver#onReceive + JVM crash

    @Test
    fun `M add crash W onReceive() { JVM crash event }`(
        @Forgery fakeThreads: List<ThreadDump>,
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphabeticalString()
        val event = JvmCrash.Rum(
            message = fakeMessage,
            throwable = fakeThrowable,
            threads = fakeThreads
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).addCrash(
            fakeMessage,
            RumErrorSource.SOURCE,
            fakeThrowable,
            fakeThreads
        )
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    @Test
    fun `M forward to RUM NDK crash event handler W onReceive() { NDK crash event }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeMessage: String,
        @StringForgery fakeStacktrace: String,
        @Forgery fakeViewEventJson: JsonObject
    ) {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val event = mutableMapOf(
            "type" to "ndk_crash",
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to fakeMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockLateCrashReporter)
            .handleNdkCrashEvent(
                event,
                testedFeature.dataWriter
            )

        verifyNoInteractions(
            mockRumMonitor,
            mockInternalLogger
        )
    }

    @Test
    fun `M consume last fatal ANR crash W consumeLastFatalAnr()`(
        @Forgery fakeViewEventJson: JsonObject,
        forge: Forge
    ) {
        // Given
        val appExitInfo = forge.anApplicationExitInfoList(mustInclude = ApplicationExitInfo.REASON_ANR)

        val mockActivityManager = mock<ActivityManager>()
        whenever(
            mockActivityManager.getHistoricalProcessExitReasons(null, 0, 0)
        ) doReturn appExitInfo
        whenever(
            appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)
        ) doReturn mockActivityManager
        val mockExecutor = mockSameThreadExecutorService()
        whenever(mockSdkCore.lastViewEvent) doReturn fakeViewEventJson
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.consumeLastFatalAnr(mockExecutor)

        // Then
        verify(mockLateCrashReporter)
            .handleAnrCrash(
                appExitInfo.first { it.reason == ApplicationExitInfo.REASON_ANR },
                fakeViewEventJson,
                testedFeature.dataWriter
            )
    }

    @Test
    fun `M not consume last fatal ANR crash W consumeLastFatalAnr() { no last view event }`(
        forge: Forge
    ) {
        // Given
        val appExitInfo = forge.anApplicationExitInfoList(mustInclude = ApplicationExitInfo.REASON_ANR)

        val mockActivityManager = mock<ActivityManager>()
        whenever(
            mockActivityManager.getHistoricalProcessExitReasons(null, 0, 0)
        ) doReturn appExitInfo
        whenever(
            appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)
        ) doReturn mockActivityManager
        val mockExecutor = mockSameThreadExecutorService()
        whenever(mockSdkCore.lastViewEvent) doReturn null
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.consumeLastFatalAnr(mockExecutor)

        // Then
        verifyNoInteractions(mockLateCrashReporter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumFeature.NO_LAST_RUM_VIEW_EVENT_AVAILABLE
        )
    }

    @Test
    fun `M not consume last fatal ANR crash W consumeLastFatalAnr() { no known ANR exit }`(
        @Forgery fakeViewEventJson: JsonObject,
        forge: Forge
    ) {
        // Given
        val appExitInfo = forge.anApplicationExitInfoList()
            .filter { it.reason != ApplicationExitInfo.REASON_ANR }

        val mockActivityManager = mock<ActivityManager>()
        whenever(
            mockActivityManager.getHistoricalProcessExitReasons(null, 0, 0)
        ) doReturn appExitInfo
        whenever(
            appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)
        ) doReturn mockActivityManager
        val mockExecutor = mockSameThreadExecutorService()
        whenever(mockSdkCore.lastViewEvent) doReturn fakeViewEventJson
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.consumeLastFatalAnr(mockExecutor)

        // Then
        verifyNoInteractions(mockLateCrashReporter, mockInternalLogger)
    }

    @Test
    fun `M log error W consumeLastFatalAnr() { error getting historical exit reasons }`(
        forge: Forge
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        val exceptionThrown = forge.anException()
        whenever(
            mockActivityManager.getHistoricalProcessExitReasons(null, 0, 0)
        ) doThrow exceptionThrown
        whenever(
            appContext.mockInstance.getSystemService(Context.ACTIVITY_SERVICE)
        ) doReturn mockActivityManager
        val mockExecutor = mockSameThreadExecutorService()
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.consumeLastFatalAnr(mockExecutor)

        // Then
        verifyNoInteractions(mockLateCrashReporter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RumFeature.FAILED_TO_GET_HISTORICAL_EXIT_REASONS,
            exceptionThrown
        )
    }

    @Test
    fun `M return true W isTrackNonFatalAnrsEnabledByDefault() { Android Q and below }`(
        @IntForgery(min = 1, max = Build.VERSION_CODES.R) fakeBuildSdkVersion: Int
    ) {
        // Given
        val mockBuildSdkVersionProvider = mock<BuildSdkVersionProvider>()
        whenever(mockBuildSdkVersionProvider.version) doReturn fakeBuildSdkVersion

        // When
        val isEnabled = RumFeature.isTrackNonFatalAnrsEnabledByDefault(mockBuildSdkVersionProvider)

        // Then
        assertThat(isEnabled).isTrue()
    }

    @Test
    fun `M return false W isTrackNonFatalAnrsEnabledByDefault() { Android R and above }`(
        @IntForgery(min = Build.VERSION_CODES.R) fakeBuildSdkVersion: Int
    ) {
        // Given
        val mockBuildSdkVersionProvider = mock<BuildSdkVersionProvider>()
        whenever(mockBuildSdkVersionProvider.version) doReturn fakeBuildSdkVersion

        // When
        val isEnabled = RumFeature.isTrackNonFatalAnrsEnabledByDefault(mockBuildSdkVersionProvider)

        // Then
        assertThat(isEnabled).isFalse()
    }

    // region FeatureEventReceiver#onReceive + logger error

    @Test
    fun `M add error W onReceive() { logger error event }`(
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        val fakeMessage = forge.anAlphabeticalString()
        val fakeAttributes = forge.aNullable { forge.exhaustiveAttributes() }
        val event = mutableMapOf(
            "type" to "logger_error",
            "message" to fakeMessage,
            "throwable" to fakeThrowable,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).addError(
            fakeMessage,
            RumErrorSource.LOGGER,
            fakeThrowable,
            fakeAttributes?.toMap() ?: emptyMap()
        )

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M log dev warning W onReceive() { logger error event + missing message field }`(
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        val fakeAttributes = forge.aNullable { forge.exhaustiveAttributes() }
        val event = mutableMapOf(
            "type" to "logger_error",
            "throwable" to fakeThrowable,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            RumFeature.LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS
        )

        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M add error with stacktrace W onReceive() { logger error with stacktrace event }`(
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val fakeStacktrace = forge.aNullable { forge.anAlphabeticalString() }
        val fakeMessage = forge.anAlphabeticalString()
        val fakeAttributes = forge.aNullable { forge.exhaustiveAttributes() }
        val event = mutableMapOf(
            "type" to "logger_error_with_stacktrace",
            "message" to fakeMessage,
            "stacktrace" to fakeStacktrace,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).addErrorWithStacktrace(
            fakeMessage,
            RumErrorSource.LOGGER,
            fakeStacktrace,
            fakeAttributes?.toMap() ?: emptyMap()
        )

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M log dev warning W onReceive() {logger error event with stacktrace + missing message field}`(
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        val fakeAttributes = forge.aNullable { forge.exhaustiveAttributes() }

        val event = mutableMapOf(
            "type" to "logger_error_with_stacktrace",
            "throwable" to fakeThrowable,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            RumFeature.LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS
        )

        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    // region FeatureEventReceiver#onReceive + webview notification

    @Test
    fun `M notify webview event received W onReceive() {webview event received}`() {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)
        val event = mapOf(
            "type" to "web_view_ingested_notification"
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).sendWebViewEvent()
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region FeatureEventReceiver#onReceive + telemetry event

    @Test
    fun `M handle telemetry event W onReceive()`(
        @Forgery fakeInternalTelemetryEvent: InternalTelemetryEvent
    ) {
        // Given
        testedFeature.onInitialize(appContext.mockInstance)

        // When
        testedFeature.onReceive(fakeInternalTelemetryEvent)

        // Then
        verify(mockRumMonitor).sendTelemetryEvent(fakeInternalTelemetryEvent)
        verifyNoMoreInteractions(mockRumMonitor)
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    private fun Forge.anApplicationExitInfoList(
        mustInclude: Int? = null
    ): List<ApplicationExitInfo> {
        val appExitInfos = aList {
            mock<ApplicationExitInfo>().apply {
                whenever(reason) doReturn anApplicationExitInfoReason()
            }
        }.toMutableList()
        if (mustInclude != null && !appExitInfos.any { it.reason == mustInclude }) {
            appExitInfos[anElementFrom(appExitInfos.indices.toList())] =
                mock<ApplicationExitInfo>().apply {
                    whenever(reason) doReturn mustInclude
                }
        }
        return appExitInfos
    }

    private fun Forge.anApplicationExitInfoReason(): Int {
        return anElementFrom(
            ApplicationExitInfo.REASON_UNKNOWN,
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            ApplicationExitInfo.REASON_PERMISSION_CHANGE,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED,
            ApplicationExitInfo.REASON_DEPENDENCY_DIED,
            ApplicationExitInfo.REASON_OTHER,
            ApplicationExitInfo.REASON_FREEZER,
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED
        )
    }

    private fun mockSameThreadExecutorService(): ExecutorService {
        return mock<ExecutorService>().apply {
            whenever(submit(any())) doAnswer {
                it.getArgument<Runnable>(0).run()
                mock()
            }
        }
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
