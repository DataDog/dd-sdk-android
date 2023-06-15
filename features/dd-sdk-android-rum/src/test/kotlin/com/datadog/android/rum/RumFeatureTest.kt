/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Application
import android.os.Build
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.rum.assertj.RumFeatureAssert
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.ndk.NdkCrashEventHandler
import com.datadog.android.rum.internal.storage.NoOpDataWriter
import com.datadog.android.rum.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListener
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.rum.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.telemetry.internal.TelemetryCoreConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.datadog.tools.unit.getFieldValue
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor

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
    lateinit var mockNdkCrashEventHandler: NdkCrashEventHandler

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )
        GlobalRum.registerIfAbsent(mockSdkCore, mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.clear()
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(RumDataWriter::class.java)
    }

    @Test
    fun `𝕄 store sample rate 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.sampleRate).isEqualTo(fakeConfiguration.sampleRate)
    }

    @Test
    fun `𝕄 set sample rate to 100 𝕎 initialize() {developer mode enabled}`() {
        // Given
        whenever(mockSdkCore.isDeveloperModeEnabled) doReturn true

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.sampleRate).isEqualTo(RumFeature.ALL_IN_SAMPLE_RATE)
        verify(mockSdkCore.internalLogger)
            .log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                RumFeature.DEVELOPER_MODE_SAMPLE_RATE_CHANGED_MESSAGE
            )
    }

    @Test
    fun `𝕄 store telemetry sample rate 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.telemetrySampleRate)
            .isEqualTo(fakeConfiguration.telemetrySampleRate)
    }

    @Test
    fun `𝕄 store background tracking 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.backgroundEventTracking)
            .isEqualTo(fakeConfiguration.backgroundEventTracking)
    }

    @Test
    fun `𝕄 store and register viewTrackingStrategy 𝕎 initialize()`(
        @Forgery fakeApplicationId: UUID
    ) {
        // Given
        val mockViewTrackingStrategy = mock<ViewTrackingStrategy>()
        fakeConfiguration =
            fakeConfiguration.copy(viewTrackingStrategy = mockViewTrackingStrategy)
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.viewTrackingStrategy).isEqualTo(mockViewTrackingStrategy)
        verify(mockViewTrackingStrategy).register(mockSdkCore, appContext.mockInstance)
    }

    @Test
    fun `𝕄 set the NoOpUserActionTrackingStrategy W initialize() {userActionTracking = false}`() {
        // Given
        fakeConfiguration =
            fakeConfiguration.copy(userActionTracking = false)
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasNoOpUserActionTrackingStrategy()
    }

    @Test
    fun `𝕄 bundle the custom attributes providers W initialize()`(
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
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasActionTargetAttributeProviders(mockProviders)
    }

    @Test
    fun `𝕄 bundle only the default providers W initialize { providers not provided }`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true
        )
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasDefaultActionTargetAttributeProviders()
    }

    @Test
    fun `𝕄 use the custom predicate 𝕎 initialize()`() {
        // Given
        val mockInteractionPredicate: InteractionPredicate = mock()
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true,
            interactionPredicate = mockInteractionPredicate
        )
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasInteractionPredicate(mockInteractionPredicate)
    }

    @Test
    fun `𝕄 use the NoOpInteractionPredicate 𝕎 initialize() { predicate not provided }`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            userActionTracking = true,
            interactionPredicate = NoOpInteractionPredicate()
        )
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyLegacy()
            .hasInteractionPredicateOfType(NoOpInteractionPredicate::class.java)
    }

    @TestTargetApi(Build.VERSION_CODES.Q)
    @Test
    fun `𝕄 build config with gestures enabled 𝕎 initialize() {Android Q}`(
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
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        RumFeatureAssert.assertThat(testedFeature)
            .hasUserActionTrackingStrategyApi29()
            .hasActionTargetAttributeProviders(mockProviders)
    }

    @Test
    fun `𝕄 store longTaskTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isEqualTo(fakeConfiguration.longTaskTrackingStrategy)
        verify(fakeConfiguration.longTaskTrackingStrategy!!)
            .register(mockSdkCore, appContext.mockInstance)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 initialize()`() {
        // Given
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration.copy(viewTrackingStrategy = null),
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 initialize()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(userActionTracking = false)
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop longTaskTrackingStrategy 𝕎 initialize()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(longTaskTrackingStrategy = null)
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isInstanceOf(NoOpTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 store eventMapper 𝕎 initialize()`() {
        // Given
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(RumDataWriter::class.java)
        val serializer = (testedFeature.dataWriter as RumDataWriter).serializer
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
    fun `𝕄 setup vital monitors 𝕎 initialize { frequency != NEVER }`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // Given
        fakeConfiguration = fakeConfiguration.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

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
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

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
    fun `𝕄 use noop viewTrackingStrategy 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 unregister strategies 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
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
    fun `𝕄 reset data writer 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpDataWriter::class.java)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 initialize vital executor 𝕎 initialize { frequency != NEVER }()`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = fakeFrequency
        )
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        val scheduledRunnables = testedFeature.vitalExecutorService.shutdownNow()
        assertThat(scheduledRunnables).isNotEmpty
    }

    @Test
    fun `𝕄 not initialize vital executor 𝕎 initialize { frequency = NEVER }()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
        )
        testedFeature = RumFeature(
            fakeApplicationId.toString(),
            fakeConfiguration,
            ndkCrashEventHandlerFactory = { mockNdkCrashEventHandler }
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 shut down vital executor 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val mockVitalExecutorService: ScheduledThreadPoolExecutor = mock()
        testedFeature.vitalExecutorService = mockVitalExecutorService

        // When
        testedFeature.onStop()

        // Then
        verify(mockVitalExecutorService).shutdownNow()
    }

    @Test
    fun `𝕄 reset vital executor 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 reset vital monitors 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableRumDebugging(true)`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.enableRumDebugging(true)

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNotNull
        verify(testedFeature.appContext as Application)
            .registerActivityLifecycleCallbacks(testedFeature.debugActivityLifecycleListener)
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableRumDebugging(true){RUM feature is not yet initialized}`() {
        // When
        testedFeature.enableRumDebugging(true)

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNull()
    }

    @Test
    fun `𝕄 disable RUM debugging 𝕎 enableRumDebugging(false)`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.enableRumDebugging(true)
        val listener = testedFeature.debugActivityLifecycleListener

        // When
        testedFeature.enableRumDebugging(false)

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNull()
        verify(testedFeature.appContext as Application)
            .unregisterActivityLifecycleCallbacks(listener)
    }

    // region FeatureEventReceiver#onReceive

    @Test
    fun `𝕄 log dev warning and do nothing else 𝕎 onReceive() { unknown type }`() {
        // Given
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(Any())

        // Then
        verify(mockInternalLogger)
            .log(
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
    fun `𝕄 log dev warning and do nothing else 𝕎 onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        val event = mapOf(
            "type" to forge.anAlphabeticalString()
        )
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
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
    fun `𝕄 log dev warning 𝕎 onReceive() { JVM crash event + missing mandatory fields }`(
        forge: Forge
    ) {
        // Given
        val event = mutableMapOf(
            "type" to "jvm_crash",
            "message" to forge.anAlphabeticalString(),
            "throwable" to forge.aThrowable()
        )
        event.remove(
            forge.anElementFrom(event.keys.filterNot { it == "type" })
        )
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 add crash 𝕎 onReceive() { JVM crash event }`(
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphabeticalString()
        val event = mutableMapOf(
            "type" to "jvm_crash",
            "message" to fakeMessage,
            "throwable" to fakeThrowable
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).addCrash(
            fakeMessage,
            RumErrorSource.SOURCE,
            fakeThrowable
        )
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    @Test
    fun `𝕄 forward to RUM NDK crash event handler 𝕎 onReceive() { NDK crash event }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeMessage: String,
        @StringForgery fakeStacktrace: String,
        @Forgery fakeViewEventJson: JsonObject
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
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
        verify(mockNdkCrashEventHandler)
            .handleEvent(
                event,
                mockSdkCore,
                testedFeature.dataWriter
            )

        verifyNoInteractions(
            mockRumMonitor,
            mockInternalLogger
        )
    }

    // region FeatureEventReceiver#onReceive + logger error

    @Test
    fun `𝕄 add error 𝕎 onReceive() { logger error event }`(
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
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
    fun `𝕄 log dev warning 𝕎 onReceive() { logger error event + missing message field }`(
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
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 add error with stacktrace 𝕎 onReceive() { logger error with stacktrace event }`(
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
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
    fun `𝕄 log dev warning 𝕎 onReceive() {logger error event with stacktrace + missing message field}`(
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
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    @Test
    fun `𝕄 notify webview event received 𝕎 onReceive() {webview event received}`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val event = mapOf(
            "type" to "web_view_ingested_notification"
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).sendWebViewEvent()
        verifyNoInteractions(mockInternalLogger)
    }

    // region FeatureEventReceiver#onReceive + telemetry event

    @Test
    fun `𝕄 handle telemetry debug event 𝕎 onReceive()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val event = mapOf(
            "type" to "telemetry_debug",
            "message" to fakeMessage
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor).sendDebugTelemetryEvent(fakeMessage)
        verifyNoMoreInteractions(mockRumMonitor)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log warning 𝕎 onReceive() { telemetry debug + message is missing }`() {
        // Given
        val event = mapOf(
            "type" to "telemetry_debug"
        )
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.TELEMETRY_MISSING_MESSAGE_FIELD
            )

        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 handle telemetry error event 𝕎 onReceive() { with throwable }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val fakeThrowable = forge.aThrowable()
        val event = mapOf(
            "type" to "telemetry_error",
            "message" to fakeMessage,
            "throwable" to fakeThrowable
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor)
            .sendErrorTelemetryEvent(fakeMessage, fakeThrowable)
        verifyNoMoreInteractions(mockRumMonitor)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 handle telemetry error event 𝕎 onReceive() { with stack and kind }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val fakeStack = forge.aNullable { aString() }
        val fakeKind = forge.aNullable { aString() }
        val event = mapOf(
            "type" to "telemetry_error",
            "message" to fakeMessage,
            "stacktrace" to fakeStack,
            "kind" to fakeKind
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor)
            .sendErrorTelemetryEvent(fakeMessage, fakeStack, fakeKind)

        verifyNoMoreInteractions(mockRumMonitor)

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log warning 𝕎 onReceive() { telemetry error + message is missing }`() {
        // Given
        val event = mapOf(
            "type" to "telemetry_error"
        )
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.TELEMETRY_MISSING_MESSAGE_FIELD
            )

        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 submit configuration telemetry 𝕎 onReceive() { telemetry configuration }`(
        @BoolForgery trackErrors: Boolean,
        @BoolForgery useProxy: Boolean,
        @BoolForgery useLocalEncryption: Boolean,
        @LongForgery(min = 0L) batchSize: Long,
        @LongForgery(min = 0L) batchUploadFrequency: Long
    ) {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val event = mapOf(
            "type" to "telemetry_configuration",
            "track_errors" to trackErrors,
            "batch_size" to batchSize,
            "batch_upload_frequency" to batchUploadFrequency,
            "use_proxy" to useProxy,
            "use_local_encryption" to useLocalEncryption
        )
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockRumMonitor)
            .sendConfigurationTelemetryEvent(
                TelemetryCoreConfiguration(
                    trackErrors = trackErrors,
                    batchSize = batchSize,
                    batchUploadFrequency = batchUploadFrequency,
                    useProxy = useProxy,
                    useLocalEncryption = useLocalEncryption
                )
            )

        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
