/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.api.forceSamplingDecision
import com.datadog.android.trace.api.partialFlushMinSpans
import com.datadog.android.trace.api.resourceName
import com.datadog.android.trace.api.serviceName
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.opentelemetry.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.opentelemetry.trace.OtelSpan
import com.datadog.opentelemetry.trace.OtelSpanContext
import com.datadog.tools.unit.completedFutureMock
import com.datadog.tools.unit.getFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelTracerBuilderProviderTest {

    lateinit var testedOtelTracerProviderBuilder: OtelTracerProvider.Builder

    @StringForgery
    lateinit var fakeServiceName: String

    @Mock
    lateinit var mockTracingFeatureScope: FeatureScope

    @Mock
    private lateinit var mockTracingFeature: TraceWriterFeature

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeInstrumentationName: String

    @StringForgery
    lateinit var fakeOperationName: String

    @Mock
    lateinit var mockTraceWriter: DatadogSpanWriter

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeatureScope
        whenever(mockTracingFeatureScope.unwrap<Feature>()) doReturn mockTracingFeature
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.service) doReturn fakeServiceName
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockTracingFeature.getCoreTracerWriter()) doReturn mockTraceWriter
        testedOtelTracerProviderBuilder = OtelTracerProvider.Builder(mockSdkCore)
    }

    // region feature checks

    @Test
    fun `M generate 64-bit span IDs and 128-bit trace IDs W startSpan()`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) spanName: String
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()

        // When
        val span = tracerProvider
            .tracerBuilder(fakeInstrumentationName)
            .build()
            .spanBuilder(spanName)
            .startSpan()

        span.end()

        // Then
        assertThat(span.spanContext.spanIdBytes).hasSize(8)
        assertThat(span.spanContext.traceId).hasSize(32)
        assertThat(span.spanContext.traceIdBytes).hasSize(16)
    }

    // endregion

    // region Tracer creation

    @Test
    fun `M return the cached tracer W get() {same instrumentation name}`() {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()
        val tracer = tracerProvider.get(fakeInstrumentationName)

        // When
        val cachedTracer = tracerProvider.get(fakeInstrumentationName)

        // Then
        assertThat(cachedTracer).isSameAs(tracer)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            OtelTracerProvider.TRACER_ALREADY_EXISTS_WARNING_MESSAGE.format(Locale.US, fakeInstrumentationName)
        )
    }

    @Test
    fun `M create different tracers W get() {different instrumentation names}`() {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()
        val tracer = tracerProvider.get(fakeInstrumentationName)

        // When
        val otherTracer = tracerProvider.get("$fakeInstrumentationName.other")

        // Then
        assertThat(otherTracer).isNotSameAs(tracer)
    }

    @Test
    fun `M return the cached tracer W get() {same instrumentation name and version}`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) instrumentationVersion: String
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()
        val tracer = tracerProvider.get(fakeInstrumentationName, instrumentationVersion)

        // When
        val cachedTracer = tracerProvider.get(fakeInstrumentationName, instrumentationVersion)

        // Then
        assertThat(cachedTracer).isSameAs(tracer)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            OtelTracerProvider.TRACER_ALREADY_EXISTS_WARNING_MESSAGE.format(Locale.US, fakeInstrumentationName)
        )
    }

    @Test
    fun `M use the spanName as resourceName W creating a span`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) spanName: String
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()

        // When
        val span = tracerProvider
            .tracerBuilder(fakeInstrumentationName)
            .build()
            .spanBuilder(spanName)
            .startSpan()
        span.end()

        // Then
        val agentContext = (span.spanContext as OtelSpanContext).delegate as DatadogSpanContext
        assertThat(agentContext.resourceName).isEqualTo(spanName)
    }

    @Test
    fun `M use the default serviceName W creating a tracer`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) spanName: String
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()

        // When
        val span = tracerProvider
            .tracerBuilder(fakeInstrumentationName)
            .build()
            .spanBuilder(spanName)
            .startSpan()
        span.end()

        // Then
        val agentContext = (span.spanContext as OtelSpanContext).delegate as DatadogSpanContext
        assertThat(agentContext.serviceName).isEqualTo(fakeServiceName)
    }

    @Test
    fun `M use the provided serviceName W setServiceName`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) spanName: String,
        @StringForgery(type = StringForgeryType.ALPHABETICAL) fakeCustomServiceName: String
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.setService(fakeCustomServiceName).build()

        // When
        val span = tracerProvider
            .tracerBuilder(fakeInstrumentationName)
            .build()
            .spanBuilder(spanName)
            .startSpan()
        span.end()

        // Then
        val agentContext = (span.spanContext as OtelSpanContext).delegate as DatadogSpanContext
        assertThat(agentContext.serviceName).isEqualTo(fakeCustomServiceName)
    }

    @Test
    fun `M use the default threshold value W creating a tracer`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder.build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: DatadogTracer = tracer.delegate

        // Then
        assertThat(coreTracer.partialFlushMinSpans).isEqualTo(OtelTracerProvider.DEFAULT_PARTIAL_MIN_FLUSH)
    }

    @Test
    fun `M use the provided flush threshold value W setPartialFlushThreshold`(@IntForgery threshold: Int) {
        // Given
        val tracer = testedOtelTracerProviderBuilder.setPartialFlushThreshold(threshold).build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: DatadogTracer = tracer.delegate

        // Then
        assertThat(coreTracer.partialFlushMinSpans).isEqualTo(threshold)
    }

    @Test
    fun `M build a valid Tracer with tags W addTag`(
        @StringForgery operation: String,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) value: String
    ) {
        // When
        val tracerProvider = testedOtelTracerProviderBuilder
            .addTag(key, value)
            .build()
        val tracer = tracerProvider.tracerBuilder(fakeInstrumentationName).build()

        // Then
        assertThat(tracer).isNotNull()
        val span = tracer.spanBuilder(operation).startSpan() as OtelSpan
        val agentSpanContext = span.datadogSpanContext as DatadogSpanContext
        assertThat(agentSpanContext.tags).containsEntry(key, value)
    }

    // endregion

    // region Sampling priority

    @Test
    fun `M use user-keep priority W buildSpan { provided keep sample rate }`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .setPartialFlushThreshold(1)
            .setSampleRate(100.0)
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DatadogSpan = span.delegate
        delegateSpan.forceSamplingDecision()
        span.end()

        // Then
        val priority = delegateSpan.samplingPriority
        assertThat(priority).isEqualTo(DatadogTracingConstants.PrioritySampling.USER_KEEP)
    }

    @Test
    fun `M use user-drop priority W buildSpan { provide not keep sample rate }`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .setPartialFlushThreshold(1)
            .setSampleRate(0.0)
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DatadogSpan = span.delegate
        delegateSpan.forceSamplingDecision()
        span.end()

        // Then
        val priority = delegateSpan.samplingPriority
        assertThat(priority).isEqualTo(DatadogTracingConstants.PrioritySampling.USER_DROP)
    }

    @Test
    fun `M use user-keep or user-not-keep priority W buildSpan { provided random sample rate }`(
        @DoubleForgery(min = 0.0, max = 100.0) sampleRate: Double,
        forge: Forge
    ) {
        // Given
        val numberOfSpans = 100
        val tracer = testedOtelTracerProviderBuilder
            .setPartialFlushThreshold(1)
            .setSampleRate(sampleRate)
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()
        val normalizedSampleRate = sampleRate / 100.0
        val expectedKeptSpans = (numberOfSpans * normalizedSampleRate).toInt()
        val expectedDroppedSpans = numberOfSpans - expectedKeptSpans

        // When
        val spans = (0 until numberOfSpans).map {
            tracer.spanBuilder(forge.anAlphabeticalString()).startSpan()
        }
        val delegatedSpans = spans.map {
            val delegatedSpan: DatadogSpan = it.delegate
            delegatedSpan.forceSamplingDecision()
            delegatedSpan
        }
        spans.forEach { it.end() }
        val droppedSpans =
            delegatedSpans.filter { it.samplingPriority == DatadogTracingConstants.PrioritySampling.USER_DROP }
        val keptSpans =
            delegatedSpans.filter { it.samplingPriority == DatadogTracingConstants.PrioritySampling.USER_KEEP }

        // Then
        assertThat(droppedSpans.size + keptSpans.size).isEqualTo(numberOfSpans)
        // The sampler does not guarantee the exact number of dropped/kept spans due to the random nature
        // of the sampling so we use an offset to allow a small margin of error
        val offset = 20
        assertThat(droppedSpans.size).isCloseTo(expectedDroppedSpans, Offset.offset(offset))
        assertThat(keptSpans.size).isCloseTo(expectedKeptSpans, Offset.offset(offset))
    }

    @Test
    fun `M use auto - keep priority W buildSpan { not provided sample rate }`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .setPartialFlushThreshold(1)
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DatadogSpan = span.delegate
        span.end()

        // Then
        val priority = delegateSpan.samplingPriority
        assertThat(priority).isEqualTo(DatadogTracingConstants.PrioritySampling.SAMPLER_KEEP)
    }

    // endregion

    // region bundle with RUM

    @Test
    fun `M build a Span with lazy Datadog context W startSpan()`(
        @Forgery fakeInitialDatadogContext: DatadogContext
    ) {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()

        val mockContextFuture = completedFutureMock(fakeInitialDatadogContext)

        whenever(mockSdkCore.getDatadogContextFuture(setOf(Feature.RUM_FEATURE_NAME)))
            .thenReturn(mockContextFuture)

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DatadogSpan = span.delegate
        val context = delegateSpan.context()
        span.end()

        // Then
        assertThat(context.tags).containsEntry(DATADOG_INITIAL_CONTEXT, mockContextFuture)
    }

    @Test
    fun `M build a Span without lazy Datadog context W startSpan() { bundleWithRum = false }`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .setBundleWithRumEnabled(false)
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()

        val context = span.delegate.context()
        span.end()

        // Then
        assertThat(context.tags).doesNotContainKey(DATADOG_INITIAL_CONTEXT)
        verify(mockSdkCore, never()).getFeature(Feature.RUM_FEATURE_NAME)
    }

    @Test
    fun `M build a Span without lazy Datadog context W startSpan() { bundleWithRum = true, RUM not initialized }`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DatadogSpan = span.delegate
        val context = delegateSpan.context()
        span.end()

        // Then
        assertThat(context.tags).doesNotContainKey(DATADOG_INITIAL_CONTEXT)
    }

    // endregion

    // region Bundle with Logs

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M propagate the active trace context W scope started and finished`() {
        // Given
        val expectedThreadName = Thread.currentThread().name
        val expectedActiveTraceContextName = "context@$expectedThreadName"
        val tracer = testedOtelTracerProviderBuilder
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()
        val delegatedTracer: DatadogTracer = tracer.delegate
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DatadogSpan = span.delegate
        val expectedTraceId = delegateSpan.context().traceId.toHexString()
        val expectedSpanId = delegateSpan.context().spanId.toString()

        // When
        val scope = delegatedTracer.activateSpan(delegateSpan)
        scope?.close()
        span.end()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            val traceContext: MutableMap<String, Any?> = mutableMapOf()
            verify(mockSdkCore, times(3))
                .updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), capture())
            secondValue.invoke(traceContext)
            val activeTraceContext = traceContext[expectedActiveTraceContextName] as Map<String, Any>
            assertThat(activeTraceContext).containsEntry("trace_id", expectedTraceId)
            assertThat(activeTraceContext).containsEntry("span_id", expectedSpanId)
            lastValue.invoke(traceContext)
            assertThat(traceContext).doesNotContainKey(expectedActiveTraceContextName)
        }
    }

    @Test
    fun `M not propagate the active trace context W scope started and finished {no active span}`() {
        // Given
        val expectedThreadName = Thread.currentThread().name
        val expectedActiveTraceContextName = "context@$expectedThreadName"
        val tracer = testedOtelTracerProviderBuilder.build()
            .tracerBuilder(fakeInstrumentationName)
            .build()

        val delegatedTracer: DatadogTracer = tracer.delegate
        spy(delegatedTracer) {
            whenever(it.activeSpan()).thenReturn(null)
        }
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()

        // When
        val delegateSpan: DatadogSpan = span.delegate
        val scope = delegatedTracer.activateSpan(delegateSpan)
        scope?.close()
        span.end()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            val traceContext: MutableMap<String, Any?> = mutableMapOf()
            verify(mockSdkCore, times(3))
                .updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), capture())
            lastValue.invoke(traceContext)
            assertThat(traceContext).doesNotContainKey(expectedActiveTraceContextName)
        }
    }

    // endregion

    // region Tracing context

    @Test
    fun `M set OpenTelemetry metadata W build() {tracing feature provides a writer}`() {
        // When
        testedOtelTracerProviderBuilder.build()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            val traceContext: MutableMap<String, Any?> = mutableMapOf()
            verify(mockSdkCore)
                .updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), eq(false), capture())
            lastValue.invoke(traceContext)
            assertThat(traceContext[OtelTracerProvider.IS_OPENTELEMETRY_ENABLED_CONFIG_KEY]).isEqualTo(true)
            assertThat(traceContext[OtelTracerProvider.OPENTELEMETRY_API_VERSION_CONFIG_KEY])
                .isEqualTo(BuildConfig.OPENTELEMETRY_API_VERSION_NAME)
        }
    }

    @Test
    fun `M not update the context W build() {tracing feature not enabled}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // When
        testedOtelTracerProviderBuilder.build()

        // Then
        verify(mockSdkCore, never()).updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), any())
    }

    @Test
    fun `M not update the context W build() {tracing feature does not provide a writer}`(
        @Mock mockFeature: Feature
    ) {
        // Given
        whenever(mockTracingFeatureScope.unwrap<Feature>()) doReturn mockFeature

        // When
        testedOtelTracerProviderBuilder.build()

        // Then
        verify(mockSdkCore, never()).updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), any())
    }

    // endregion

    private interface TraceWriterFeature : Feature, InternalCoreWriterProvider

    private companion object {

        private const val DATADOG_INITIAL_CONTEXT: String = "_dd.datadog_initial_context"

        private val Tracer.delegate: DatadogTracer
            get() = getFieldValue("tracer")

        private val Span.delegate: DatadogSpan
            get() = getFieldValue("delegate")
    }
}
