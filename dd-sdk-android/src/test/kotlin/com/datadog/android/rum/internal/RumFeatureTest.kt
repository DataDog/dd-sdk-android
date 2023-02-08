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
import com.datadog.android.core.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.event.NoOpEventMapper
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
import com.datadog.android.v2.core.storage.NoOpDataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
    lateinit var fakeConfiguration: Configuration.Feature.RUM

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

        testedFeature =
            RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(RumDataWriter::class.java)
    }

    @Test
    fun `𝕄 store sampling rate 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.samplingRate).isEqualTo(fakeConfiguration.samplingRate)
    }

    @Test
    fun `𝕄 store telemetry sampling rate 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.telemetrySamplingRate)
            .isEqualTo(fakeConfiguration.telemetrySamplingRate)
    }

    @Test
    fun `𝕄 store background tracking 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.backgroundEventTracking)
            .isEqualTo(fakeConfiguration.backgroundEventTracking)
    }

    @Test
    fun `𝕄 store and register viewTrackingStrategy 𝕎 initialize()`() {
        // Given
        val mockViewTrackingStrategy = mock<ViewTrackingStrategy>()
        fakeConfiguration =
            fakeConfiguration.copy(viewTrackingStrategy = mockViewTrackingStrategy)
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.viewTrackingStrategy).isEqualTo(mockViewTrackingStrategy)
        verify(mockViewTrackingStrategy).register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 store userActionTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isEqualTo(fakeConfiguration.userActionTrackingStrategy)
        verify(fakeConfiguration.userActionTrackingStrategy!!)
            .register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 store longTaskTrackingStrategy 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isEqualTo(fakeConfiguration.longTaskTrackingStrategy)
        verify(fakeConfiguration.longTaskTrackingStrategy!!)
            .register(appContext.mockInstance)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 initialize()`() {
        // Given
        testedFeature = RumFeature(
            fakeConfiguration.copy(viewTrackingStrategy = null),
            coreFeature.mockInstance,
            mockNdkCrashEventHandler
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 initialize()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(userActionTrackingStrategy = null)
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop longTaskTrackingStrategy 𝕎 initialize()`() {
        // Given
        fakeConfiguration = fakeConfiguration.copy(longTaskTrackingStrategy = null)
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.longTaskTrackingStrategy)
            .isInstanceOf(NoOpTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 store eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.rumEventMapper).isSameAs(fakeConfiguration.rumEventMapper)
    }

    @ParameterizedTest
    @EnumSource(VitalsUpdateFrequency::class, names = ["NEVER"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 setup vital monitors 𝕎 initialize { frequency != NEVER }`(
        fakeFrequency: VitalsUpdateFrequency
    ) {
        // Given
        fakeConfiguration = fakeConfiguration.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

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
        // Given
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
        )
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

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
        fakeConfiguration = fakeConfiguration.copy(vitalsMonitorUpdateFrequency = fakeFrequency)
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

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
        fakeConfiguration = fakeConfiguration.copy(
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.NEVER
        )
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        verifyZeroInteractions(mockChoreographer)
    }

    @Test
    fun `𝕄 use noop viewTrackingStrategy 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 use noop userActionTrackingStrategy 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `𝕄 unregister strategies 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())
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
    fun `𝕄 reset eventMapper 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.rumEventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @Test
    fun `𝕄 reset data writer 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

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
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

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
        testedFeature = RumFeature(fakeConfiguration, coreFeature.mockInstance, mockNdkCrashEventHandler)

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 shut down vital executor 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())
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
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.vitalExecutorService)
            .isInstanceOf(NoOpScheduledExecutorService::class.java)
    }

    @Test
    fun `𝕄 reset vital monitors 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.cpuVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.memoryVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
        assertThat(testedFeature.frameRateVitalMonitor).isInstanceOf(NoOpVitalMonitor::class.java)
    }

    @Test
    fun `𝕄 enable RUM debugging 𝕎 enableDebugging()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())

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
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())
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

        verifyZeroInteractions(
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

        verifyZeroInteractions(
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

        verifyZeroInteractions(
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

        verifyZeroInteractions(
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
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance, mock())
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

        verifyZeroInteractions(
            rumMonitor.mockInstance,
            logger.mockInternalLogger
        )
    }

    @Test
    fun `𝕄 add error 𝕎 onReceive() { logger error event }`(
        forge: Forge
    ) {
        // Given
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
        verify(rumMonitor.mockInstance)
            .addError(
                fakeMessage,
                RumErrorSource.LOGGER,
                fakeThrowable,
                fakeAttributes?.toMap() ?: emptyMap()
            )

        verifyZeroInteractions(
            mockSdkCore,
            logger.mockInternalLogger
        )
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

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyZeroInteractions(
            mockSdkCore,
            rumMonitor.mockInstance
        )
    }

    @Test
    fun `𝕄 add error with stacktrace 𝕎 onReceive() { logger error with stacktrace event }`(
        forge: Forge
    ) {
        // Given
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
        verify(rumMonitor.mockInstance)
            .addErrorWithStacktrace(
                fakeMessage,
                RumErrorSource.LOGGER,
                fakeStacktrace,
                fakeAttributes?.toMap() ?: emptyMap()
            )

        verifyZeroInteractions(
            mockSdkCore,
            logger.mockInternalLogger
        )
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

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                RumFeature.LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyZeroInteractions(
            mockSdkCore,
            rumMonitor.mockInstance
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
