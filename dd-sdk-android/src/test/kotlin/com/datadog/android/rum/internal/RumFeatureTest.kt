/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import android.view.Choreographer
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.ndk.NdkCrashEventHandler
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalFrameCallback
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.ScheduledThreadPoolExecutor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureTest {

    private lateinit var testedFeature: RumFeature

    @Forgery
    lateinit var fakeConfigurationFeature: Configuration.Feature.RUM

    @Mock
    lateinit var mockChoreographer: Choreographer

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockNdkCrashEventHandler: NdkCrashEventHandler

    @BeforeEach
    fun `set up RUM`() {
        doNothing().whenever(mockChoreographer).postFrameCallback(any())
        mockChoreographerInstance(mockChoreographer)

        testedFeature = RumFeature(mockSdkCore, coreFeature.mockInstance, mockNdkCrashEventHandler)
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(RumDataWriter::class.java)
    }

    @Test
    fun `𝕄 store sampling rate 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.samplingRate).isEqualTo(fakeConfigurationFeature.samplingRate)
    }

    @Test
    fun `𝕄 store telemetry sampling rate 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.telemetrySamplingRate)
            .isEqualTo(fakeConfigurationFeature.telemetrySamplingRate)
    }

    @Test
    fun `𝕄 store background tracking 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.backgroundEventTracking)
            .isEqualTo(fakeConfigurationFeature.backgroundEventTracking)
    }

    @Test
    fun `𝕄 store and register viewTrackingStrategy 𝕎 initialize()`() {
        // When
        val mockViewTrackingStrategy = mock<ViewTrackingStrategy>()
        fakeConfigurationFeature =
            fakeConfigurationFeature.copy(viewTrackingStrategy = mockViewTrackingStrategy)
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.viewTrackingStrategy).isEqualTo(mockViewTrackingStrategy)
        verify(mockViewTrackingStrategy).register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 store userActionTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.userActionTrackingStrategy)
        verify(fakeConfigurationFeature.userActionTrackingStrategy!!)
            .register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 store longTaskTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.longTaskTrackingStrategy)
        verify(fakeConfigurationFeature.longTaskTrackingStrategy!!)
            .register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(viewTrackingStrategy = null)

        // When
        testedFeature.initialize(appContext.mockInstance, config)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(userActionTrackingStrategy = null)

        // When
        testedFeature.initialize(appContext.mockInstance, config)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop longTaskTrackingStrategy 𝕎 initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(longTaskTrackingStrategy = null)

        // When
        testedFeature.initialize(appContext.mockInstance, config)

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isInstanceOf(NoOpTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 store eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.rumEventMapper).isSameAs(fakeConfigurationFeature.rumEventMapper)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 setup vital monitors 𝕎 initialize { frequency != NEVER }`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )

        // Then
        assertThat(testedFeature.cpuVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor)
            .isInstanceOf(AggregatingVitalMonitor::class.java)
        argumentCaptor<Choreographer.FrameCallback> {
            verify(mockChoreographer).postFrameCallback(capture())
            assertThat(firstValue).isInstanceOf(VitalFrameCallback::class.java)
        }
    }

    @Test
    fun `M not initialize the vital monitors W initialize { frequency = NEVER }`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
            )
        )

        // Then
        assertThat(testedFeature.cpuVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor)
            .isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 register choreographer callback safely 𝕎 initialize { frequency != NEVER }()`(
        fakeFrequency: VitalsUpdateFrequency,
        @StringForgery message: String
    ) {
        // Given
        doThrow(IllegalStateException(message)).whenever(mockChoreographer).postFrameCallback(any())

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        )

        // Then
        argumentCaptor<Choreographer.FrameCallback> {
            verify(mockChoreographer).postFrameCallback(capture())
            assertThat(firstValue).isInstanceOf(VitalFrameCallback::class.java)
        }
    }

    @Test
    fun `𝕄 not register choreographer callback 𝕎 initialize { frequency = NEVER }()`(
        @StringForgery message: String
    ) {
        // Given
        doThrow(IllegalStateException(message)).whenever(mockChoreographer).postFrameCallback(any())

        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
            )
        )

        // Then
        verifyNoInteractions(mockChoreographer)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 unregister strategies 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        val mockActionTrackingStrategy: UserActionTrackingStrategy = mock()
        val mockViewTrackingStrategy: ViewTrackingStrategy = mock()
        val mockLongTaskTrackingStrategy: TrackingStrategy = mock()
        testedFeature.actionTrackingStrategy = mockActionTrackingStrategy
        testedFeature.viewTrackingStrategy = mockViewTrackingStrategy
        testedFeature.longTaskTrackingStrategy = mockLongTaskTrackingStrategy

        // When
        testedFeature.stop()

        // Then
        verify(mockActionTrackingStrategy).unregister(appContext.mockInstance)
        verify(mockViewTrackingStrategy).unregister(appContext.mockInstance)
        verify(mockLongTaskTrackingStrategy).unregister(appContext.mockInstance)
    }

    @Test
    fun `𝕄 reset eventMapper 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.rumEventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @Test
    fun `𝕄 reset data writer 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpDataWriter::class.java)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 initialize vital executor 𝕎 initialize { frequency != NEVER }()`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = fakeFrequency
            )
        )

        // Then
        val scheduledRunnables = testedFeature.vitalExecutorService.shutdownNow()
        assertThat(scheduledRunnables).isNotEmpty
    }

    @Test
    fun `𝕄 not initialize vital executor 𝕎 initialize { frequency = NEVER }()`() {
        // When
        testedFeature.initialize(
            appContext.mockInstance,
            fakeConfigurationFeature.copy(
                vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
            )
        )

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 shut down vital executor 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        val mockVitalExecutorService: ScheduledThreadPoolExecutor = mock()
        testedFeature.vitalExecutorService = mockVitalExecutorService

        // When
        testedFeature.stop()

        // Then
        verify(mockVitalExecutorService).shutdownNow()
    }

    @Test
    fun `𝕄 reset vital executor 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 reset vital monitors 𝕎 stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableDebugging()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.enableDebugging()

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNotNull
        verify(testedFeature.appContext as Application)
            .registerActivityLifecycleCallbacks(testedFeature.debugActivityLifecycleListener)
    }

    @Test
    fun `𝕄 disable RUM debugging 𝕎 disableDebugging()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        testedFeature.enableDebugging()
        val listener = testedFeature.debugActivityLifecycleListener

        // When
        testedFeature.disableDebugging()

        // Then
        assertThat(testedFeature.debugActivityLifecycleListener).isNull()
        verify(testedFeature.appContext as Application)
            .unregisterActivityLifecycleCallbacks(listener)
    }

    // region FeatureEventReceiver#onReceive + JVM crash

    @Test
    fun `𝕄 log dev warning and do nothing else 𝕎 onReceive() { unknown type }`() {
        // When
        testedFeature.onReceive(Any())

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.UNSUPPORTED_EVENT_TYPE.format(
                    Locale.US,
                    Any()::class.java.canonicalName
                )
            )

        verifyNoInteractions(
            mockSdkCore,
            rumMonitor.mockInstance
        )
    }

    @Test
    fun `𝕄 log dev warning and do nothing else 𝕎 onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        val event = mapOf(
            "type" to forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(
                    Locale.US,
                    event["type"]
                )
            )

        verifyNoInteractions(
            mockSdkCore,
            rumMonitor.mockInstance
        )
    }

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

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyNoInteractions(
            mockSdkCore,
            rumMonitor.mockInstance
        )
    }

    @Test
    fun `𝕄 add crash 𝕎 onReceive() { JVM crash event }`(
        forge: Forge
    ) {
        // Given
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
        verify(rumMonitor.mockInstance)
            .addCrash(
                fakeMessage,
                RumErrorSource.SOURCE,
                fakeThrowable
            )

        verifyNoInteractions(
            mockSdkCore,
            logger.mockInternalLogger
        )
    }

    @Test
    fun `𝕄 forward to RUM NDK crash event handler 𝕎 onReceive() { NDK crash event }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeMessage: String,
        @StringForgery fakeStacktrace: String,
        @Forgery fakeViewEventJson: JsonObject
    ) {
        // Given
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
            rumMonitor.mockInstance,
            mockSdkCore,
            logger.mockInternalLogger
        )
    }

    // endregion

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature, rumMonitor, logger)
        }
    }
}
