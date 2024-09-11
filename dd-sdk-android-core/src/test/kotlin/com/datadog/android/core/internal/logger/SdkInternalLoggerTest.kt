/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.logger

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.internal.telemetry.TelemetryEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkInternalLoggerTest {

    @Mock
    lateinit var mockUserLogHandler: LogcatLogHandler

    @Mock
    lateinit var mockMaintainerLogHandler: LogcatLogHandler

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceName: String

    private lateinit var testedInternalLogger: SdkInternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.name) doReturn fakeInstanceName

        testedInternalLogger = SdkInternalLogger(
            sdkCore = mockSdkCore,
            userLogHandlerFactory = { mockUserLogHandler },
            maintainerLogHandlerFactory = { mockMaintainerLogHandler }
        )
    }

    fun callWithLambda(i: Int, lambda: () -> String) {
        if (i == 0) {
            print(lambda())
        }
    }

    fun callWithString(i: Int, str: String) {
        if (i == 0) {
            print(str)
        }
    }

    // region Target.USER

    @Test
    fun `M send user log W log { USER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockUserLogHandler.canLog(any())) doReturn true

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.USER,
            mockLambda,
            fakeThrowable
        )

        // Then
        verify(mockUserLogHandler)
            .log(
                fakeLevel.toLogLevel(),
                "[$fakeInstanceName]: $fakeMessage",
                fakeThrowable
            )
    }

    @Test
    fun `M send user log only once W log { USER target, onlyOnce=true }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockUserLogHandler.canLog(any())) doReturn true

        // When
        repeat(10) {
            testedInternalLogger.log(
                fakeLevel,
                InternalLogger.Target.USER,
                mockLambda,
                fakeThrowable,
                true
            )
        }

        // Then
        verify(mockUserLogHandler)
            .log(
                fakeLevel.toLogLevel(),
                "[$fakeInstanceName]: $fakeMessage",
                fakeThrowable
            )
    }

    @Test
    fun `M send user log with condition W log { USER target }`(
        @IntForgery(min = Log.VERBOSE, max = (Log.ASSERT + 1)) sdkVerbosity: Int
    ) {
        // Given
        Datadog.setVerbosity(sdkVerbosity)

        // When
        testedInternalLogger = SdkInternalLogger(
            sdkCore = mockSdkCore,
            maintainerLogHandlerFactory = { mockMaintainerLogHandler }
        )

        // Then
        val predicate = testedInternalLogger.userLogger.predicate
        for (i in 0..10) {
            if (i >= sdkVerbosity) {
                assertThat(predicate(i)).isTrue
            } else {
                assertThat(predicate(i)).isFalse
            }
        }
    }

    @Test
    fun `M not evaluate lambda W log { USER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockUserLogHandler.canLog(any())) doReturn false

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.USER,
            mockLambda,
            fakeThrowable,
            true
        )

        // Then
        verify(mockLambda, never()).invoke()
    }

    // endregion

    @Test
    fun `M send maintainer log W log { MAINTAINER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockMaintainerLogHandler.canLog(any())) doReturn true

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.MAINTAINER,
            mockLambda,
            fakeThrowable
        )

        // Then
        verify(mockMaintainerLogHandler).log(
            fakeLevel.toLogLevel(),
            "[$fakeInstanceName]: $fakeMessage",
            fakeThrowable
        )
    }

    @Test
    fun `M send maintainer log only once W log { MAINTAINER target, onlyOnce=true }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockMaintainerLogHandler.canLog(any())) doReturn true

        // When
        repeat(10) {
            testedInternalLogger.log(
                fakeLevel,
                InternalLogger.Target.MAINTAINER,
                mockLambda,
                fakeThrowable,
                true
            )
        }

        // Then
        verify(mockMaintainerLogHandler).log(
            fakeLevel.toLogLevel(),
            "[$fakeInstanceName]: $fakeMessage",
            fakeThrowable
        )
    }

    @Test
    fun `M send telemetry log W log { TELEMETRY target, no throwable + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            null
        )

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val logEvent = firstValue["event"] as TelemetryEvent.Log.Debug
            assertThat(logEvent.message).isEqualTo(fakeMessage)
            assertThat(logEvent.additionalProperties).isNull()
        }
    }

    @Test
    fun `M send telemetry log W log { TELEMETRY target, additional properties + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeAdditionalProperties = forge.aMap {
            forge.anAlphabeticalString() to forge.aNullable { anAlphabeticalString() }
        }
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            null,
            additionalProperties = fakeAdditionalProperties
        )

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val logEvent = firstValue["event"] as TelemetryEvent.Log.Debug
            assertThat(logEvent.message).isEqualTo(fakeMessage)
            assertThat(logEvent.additionalProperties).isEqualTo(fakeAdditionalProperties)
        }
    }

    @Test
    fun `M send telemetry log W log { TELEMETRY target, additional prop empty + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            null,
            additionalProperties = emptyMap()
        )

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val logEvent = firstValue["event"] as TelemetryEvent.Log.Debug
            assertThat(logEvent.message).isEqualTo(fakeMessage)
            assertThat(logEvent.additionalProperties).isEmpty()
        }
    }

    @Test
    fun `M send telemetry log W log { TELEMETRY target, no throwable + warn or error }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeAdditionalProperties = forge.exhaustiveAttributes()
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.WARN, InternalLogger.Level.ERROR)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            null,
            additionalProperties = fakeAdditionalProperties
        )

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val logEvent = firstValue["event"] as TelemetryEvent.Log.Error
            assertThat(logEvent.message).isEqualTo(fakeMessage)
            assertThat(logEvent.additionalProperties).isEqualTo(fakeAdditionalProperties)
        }
    }

    @Test
    fun `M send telemetry log W log { TELEMETRY target, with throwable}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeAdditionalProperties = forge.exhaustiveAttributes()
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aThrowable()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            fakeThrowable,
            additionalProperties = fakeAdditionalProperties
        )

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val logEvent = firstValue["event"] as TelemetryEvent.Log.Error
            assertThat(logEvent.message).isEqualTo(fakeMessage)
            assertThat(logEvent.error).isEqualTo(fakeThrowable)
            assertThat(logEvent.additionalProperties).isEqualTo(fakeAdditionalProperties)
        }
    }

    @Test
    fun `M send telemetry log only once W log { TELEMETRY target, onlyOnce=true}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        repeat(10) {
            testedInternalLogger.log(
                fakeLevel,
                InternalLogger.Target.TELEMETRY,
                mockLambda,
                null,
                true
            )
        }

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val logEvent = firstValue["event"] as TelemetryEvent.Log.Debug
            assertThat(logEvent.message).isEqualTo(fakeMessage)
        }
    }

    @Test
    fun `M send metric W metric() {sampling 100 percent}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val fakeAdditionalProperties = forge.exhaustiveAttributes()
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage

        // When
        testedInternalLogger.logMetric(
            mockLambda,
            fakeAdditionalProperties,
            100.0f
        )

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val metricEvent = firstValue["event"] as TelemetryEvent.Metric
            assertThat(metricEvent.message).isEqualTo(fakeMessage)
            assertThat(metricEvent.additionalProperties).isEqualTo(fakeAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W metric() {sampling x percent}`(
        @StringForgery fakeMessage: String,
        @FloatForgery(25f, 75f) fakeSampleRate: Float,
        forge: Forge
    ) {
        // Given
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val fakeAdditionalProperties = forge.exhaustiveAttributes()
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val repeatCount = 100
        val expectedCallCount = (repeatCount * fakeSampleRate / 100f).toInt()
        val marginOfError = (repeatCount * 0.25f).toInt()

        // When
        repeat(100) {
            testedInternalLogger.logMetric(
                mockLambda,
                fakeAdditionalProperties,
                fakeSampleRate
            )
        }

        // Then
        val count = mockingDetails(mockRumFeatureScope).invocations.filter { it.method.name == "sendEvent" }.size
        assertThat(count).isCloseTo(expectedCallCount, offset(marginOfError))
    }

    @Test
    fun `M not send metric W metric() {sampling 0 percent}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val fakeAdditionalProperties = forge.exhaustiveAttributes()
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage

        // When
        testedInternalLogger.logMetric(
            mockLambda,
            fakeAdditionalProperties,
            0.0f
        )

        // Then
        verify(mockRumFeatureScope, never()).sendEvent(any())
    }

    @Test
    fun `M do nothing W metric { rum feature not initialized }`(
        @StringForgery fakeMessage: String,
        @FloatForgery(0f, 100f) fakeSampleRate: Float,
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        val fakeAdditionalProperties = forge.exhaustiveAttributes()
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage

        // When
        assertDoesNotThrow {
            testedInternalLogger.logMetric(
                mockLambda,
                fakeAdditionalProperties,
                fakeSampleRate
            )
        }
    }

    @Test
    fun `M send api usage telemetry W logApiUsage() { sampling rate 100 percent }`(
        @Forgery fakeApiUsageTelemetryEvent: TelemetryEvent.ApiUsage
    ) {
        // Given
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.logApiUsage(fakeApiUsageTelemetryEvent, 100.0f)

        // Then
        argumentCaptor<Map<String, Any>>() {
            verify(mockRumFeatureScope).sendEvent(capture())
            assertThat(firstValue.size).isEqualTo(2)
            assertThat(firstValue["type"]).isEqualTo("telemetry_event")
            val apiUsageEvent = firstValue["event"] as TelemetryEvent.ApiUsage
            assertThat(apiUsageEvent).isEqualTo(fakeApiUsageTelemetryEvent)
        }
    }

    @Test
    fun `M send api usage telemetry W metric() {sampling x percent}`(
        @FloatForgery(25f, 75f) fakeSampleRate: Float,
        @Forgery fakeApiUsageTelemetryEvent: TelemetryEvent.ApiUsage
    ) {
        // Given
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        val repeatCount = 100
        val expectedCallCount = (repeatCount * fakeSampleRate / 100f).toInt()
        val marginOfError = (repeatCount * 0.25f).toInt()

        // When
        repeat(100) {
            testedInternalLogger.logApiUsage(
                fakeApiUsageTelemetryEvent,
                fakeSampleRate
            )
        }

        // Then
        val count = mockingDetails(mockRumFeatureScope).invocations.filter { it.method.name == "sendEvent" }.size
        assertThat(count).isCloseTo(expectedCallCount, offset(marginOfError))
    }

    @Test
    fun `M not send any api usage telemetry W logApiUsage() {sampling 0 percent}`(
        @Forgery fakeApiUsageTelemetryEvent: TelemetryEvent.ApiUsage
    ) {
        // Given
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.logApiUsage(
            fakeApiUsageTelemetryEvent,
            0.0f
        )

        // Then
        verify(mockRumFeatureScope, never()).sendEvent(any())
    }

    @Test
    fun `M do nothing W logApiUsage { rum feature not initialized }`(
        @FloatForgery(0f, 100f) fakeSampleRate: Float,
        @Forgery fakeApiUsageTelemetryEvent: TelemetryEvent.ApiUsage
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        // When
        assertDoesNotThrow {
            testedInternalLogger.logApiUsage(
                fakeApiUsageTelemetryEvent,
                fakeSampleRate
            )
        }
    }

    @Test
    fun `M create PerformanceMetric W startPerformanceMeasure() {MethodCalled, 100 percent}`(
        @StringForgery fakeCaller: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        val startNs = System.nanoTime()

        // When
        val result = testedInternalLogger.startPerformanceMeasure(
            fakeCaller,
            TelemetryMetricType.MethodCalled,
            100f,
            fakeOperation
        )
        val endNs = System.nanoTime()

        // Then
        val methodCalledTelemetry = result as? MethodCalledTelemetry
        checkNotNull(methodCalledTelemetry)
        assertThat(methodCalledTelemetry.callerClass).isEqualTo(fakeCaller)
        assertThat(methodCalledTelemetry.operationName).isEqualTo(fakeOperation)
        assertThat(methodCalledTelemetry.internalLogger).isSameAs(testedInternalLogger)
        assertThat(methodCalledTelemetry.startTime).isBetween(startNs, endNs)
    }

    @Test
    fun `M apply sample rate W startPerformanceMeasure() {MethodCalled, sampled}`(
        @StringForgery fakeCaller: String,
        @StringForgery fakeOperation: String,
        @FloatForgery(min = 25f, max = 75f) fakeSampleRate: Float
    ) {
        // Given
        var sampleCount = 0
        val repeatCount = 256
        val expectedSampledCount = (repeatCount * fakeSampleRate).toInt() / 100

        // When
        repeat(repeatCount) {
            val result = testedInternalLogger.startPerformanceMeasure(
                fakeCaller,
                TelemetryMetricType.MethodCalled,
                fakeSampleRate,
                fakeOperation
            )
            if (result != null) {
                sampleCount++
            }
        }

        // Then
        val margin = (repeatCount / 8) // Allow a 12.5% margin of error
        assertThat(sampleCount).isCloseTo(expectedSampledCount, offset(margin))
    }

    private fun InternalLogger.Level.toLogLevel(): Int {
        return when (this) {
            InternalLogger.Level.VERBOSE -> Log.VERBOSE
            InternalLogger.Level.DEBUG -> Log.DEBUG
            InternalLogger.Level.INFO -> Log.INFO
            InternalLogger.Level.WARN -> Log.WARN
            InternalLogger.Level.ERROR -> Log.ERROR
        }
    }
}
