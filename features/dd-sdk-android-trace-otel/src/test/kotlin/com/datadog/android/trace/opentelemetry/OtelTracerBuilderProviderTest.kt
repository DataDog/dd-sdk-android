/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.concurrent.CompletableFuture
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.internal.SpanAttributes
import com.datadog.android.trace.opentelemetry.internal.NoOpCoreTracerWriter
import com.datadog.android.trace.opentelemetry.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentelemetry.trace.OtelSpan
import com.datadog.opentelemetry.trace.OtelSpanContext
import com.datadog.opentelemetry.trace.OtelTracer
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import com.datadog.trace.api.Config
import com.datadog.trace.api.config.TracerConfig
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.bootstrap.instrumentation.api.AgentScopeManager
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.CoreTracer
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    lateinit var fakeServiceName: String

    @Mock
    lateinit var mockTracingFeatureScope: FeatureScope

    @Mock
    lateinit var mockTracingFeature: StubTracingFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeInstrumentationName: String

    @StringForgery
    lateinit var fakeOperationName: String

    @Mock
    lateinit var mockTraceWriter: Writer

    lateinit var fakeRumContext: MutableMap<String, String>

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeApplicationId: String

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeSessionId: String

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeViewId: String

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeActionId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        whenever(
            mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        ) doReturn mockTracingFeatureScope
        fakeRumContext = mutableMapOf(
            OtelTracerProvider.RUM_APPLICATION_ID_KEY to fakeApplicationId,
            OtelTracerProvider.RUM_SESSION_ID_KEY to fakeSessionId,
            OtelTracerProvider.RUM_VIEW_ID_KEY to fakeViewId,
            OtelTracerProvider.RUM_ACTION_ID_KEY to fakeActionId
        )
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn fakeRumContext
        whenever(mockTracingFeatureScope.unwrap<Feature>()) doReturn mockTracingFeature
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mock()
        whenever(mockSdkCore.service) doReturn fakeServiceName
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockTracingFeature.getCoreTracerWriter()) doReturn mockTraceWriter
        testedOtelTracerProviderBuilder = OtelTracerProvider.Builder(mockSdkCore)
    }

    // region feature checks

    @Test
    fun `M log a user error W build { TracingFeature not enabled }`() {
        // GIVEN
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // WHEN
        val tracer = testedOtelTracerProviderBuilder.build()

        // THEN
        assertThat(tracer).isNotNull
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            OtelTracerProvider.TRACING_NOT_ENABLED_ERROR_MESSAGE
        )
    }

    @Test
    fun `M log a maintainer error W build { TracingFeature not implementing InternalCoreTracerWriterProvider }`() {
        // GIVEN
        whenever(mockTracingFeatureScope.unwrap<Feature>()) doReturn mock()

        // WHEN
        val tracer = testedOtelTracerProviderBuilder.build()

        // THEN
        assertThat(tracer).isNotNull
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            OtelTracerProvider.WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE
        )
    }

    @Test
    fun `M use a NoOpCoreTracerWriter W build { TracingFeature not enabled }`() {
        // GIVEN
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // WHEN
        val tracer = testedOtelTracerProviderBuilder.build()

        // THEN
        assertThat(tracer).isNotNull
        val coreTracer: CoreTracer = tracer.getFieldValue("coreTracer")
        val writer: Writer = coreTracer.getFieldValue("writer")
        assertThat(writer).isInstanceOf(NoOpWriter::class.java)
    }

    @Test
    fun `M use the feature writer W build { TracingFeature enabled }`() {
        // WHEN
        val tracer = testedOtelTracerProviderBuilder.build()

        // THEN
        assertThat(tracer).isNotNull
        val coreTracer: CoreTracer = tracer.getFieldValue("coreTracer")
        val writer: Writer = coreTracer.getFieldValue("writer")
        assertThat(writer).isSameAs(mockTraceWriter)
    }

    @Test
    fun `M log a user error W build { default service name not available }`() {
        // GIVEN
        whenever(mockSdkCore.service) doReturn ""

        // WHEN
        testedOtelTracerProviderBuilder.build()

        // THEN
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            OtelTracerProvider.DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE
        )
    }

    // endregion

    // region ID generation

    @Test
    fun `M build tracers which generate Spans with 64 bits long ids W build`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) spanName: String
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder
            .build()

        // When
        val span = tracerProvider
            .tracerBuilder(fakeInstrumentationName)
            .build()
            .spanBuilder(spanName)
            .startSpan()

        // Then
        assertThat(span.spanContext.spanIdBytes.size).isEqualTo(8)
    }

    @Test
    fun `M build tracers which generate Spans with 128 bits long trace ids W build`(
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
        val traceId = span.spanContext.traceId

        // Then
        assertThat(traceId).hasSize(32)
        assertThat(span.spanContext.traceIdBytes.size).isEqualTo(16)
    }

    // endregion

    // region Tracer creation

    @Test
    fun `M create a single tracer for same instrumentation name W get`(forge: Forge) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()
        val tracer = tracerProvider.get(fakeInstrumentationName)

        // Then
        val times = forge.aTinyInt()
        repeat(times) {
            val newTracer = tracerProvider.get(fakeInstrumentationName)
            assertThat(newTracer).isSameAs(tracer)
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            OtelTracerProvider.TRACER_ALREADY_EXISTS_WARNING_MESSAGE.format(Locale.US, fakeInstrumentationName),
            mode = times(times)
        )
    }

    @Test
    fun `M create different tracers for different instrumentation name W get`(forge: Forge) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()
        val tracer = tracerProvider.get(fakeInstrumentationName)

        // Then
        val times = forge.aTinyInt()
        repeat(times) {
            val newTracer = tracerProvider.get(forge.anAlphabeticalString())
            assertThat(newTracer).isNotSameAs(tracer)
        }
    }

    @Test
    fun `M create a single tracer for same instrumentation name W get { same instrumentationVersion }`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) instrumentationVersion: String,
        forge: Forge
    ) {
        // Given
        val tracerProvider = testedOtelTracerProviderBuilder.build()
        val tracer = tracerProvider.get(fakeInstrumentationName, instrumentationVersion)

        // Then
        val times = forge.aTinyInt()
        repeat(times) {
            val newTracer = tracerProvider.get(fakeInstrumentationName, instrumentationVersion)
            assertThat(newTracer).isSameAs(tracer)
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            OtelTracerProvider.TRACER_ALREADY_EXISTS_WARNING_MESSAGE.format(Locale.US, fakeInstrumentationName),
            mode = times(times)
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
        val agentContext = (span.spanContext as OtelSpanContext).delegate as DDSpanContext
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
        val agentContext = (span.spanContext as OtelSpanContext).delegate as DDSpanContext
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
        val agentContext = (span.spanContext as OtelSpanContext).delegate as DDSpanContext
        assertThat(agentContext.serviceName).isEqualTo(fakeCustomServiceName)
    }

    @Test
    fun `M use the default threshold value W creating a tracer`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder.build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")

        // Then
        assertThat(coreTracer.partialFlushMinSpans).isEqualTo(OtelTracerProvider.DEFAULT_PARTIAL_MIN_FLUSH)
    }

    @Test
    fun `M use the provided flush threshold value W setPartialFlushThreshold`(@IntForgery threshold: Int) {
        // Given
        val tracer = testedOtelTracerProviderBuilder.setPartialFlushThreshold(threshold).build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")

        // Then
        assertThat(coreTracer.partialFlushMinSpans).isEqualTo(threshold)
    }

    @Test
    fun `M set correct propagating style W setting tracing header types`(forge: Forge) {
        // Given
        val tracingHeaderStyles = forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        val tracerProvider = testedOtelTracerProviderBuilder
            .setTracingHeaderTypes(tracingHeaderStyles)
            .build()

        // Then
        tracerProvider.tracerBuilder(fakeInstrumentationName).build()
        val properties = testedOtelTracerProviderBuilder.properties()

        val injectionStyles = properties
            .getProperty(TracerConfig.PROPAGATION_STYLE_INJECT)
            .toString()
            .split(",")
            .toSet()
        val extractionStyles = properties
            .getProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT)
            .toString()
            .split(",")
            .toSet()

        assertThat(injectionStyles).isEqualTo(tracingHeaderStyles.map { it.headerType }.toSet())
        assertThat(extractionStyles).isEqualTo(tracingHeaderStyles.map { it.headerType }.toSet())
    }

    @Test
    fun `M use default propagating style W build`() {
        // Given
        val expectedDefaultPropagationStyles = setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
        val tracerProvider = testedOtelTracerProviderBuilder
            .build()

        // Then
        tracerProvider.tracerBuilder(fakeInstrumentationName).build()
        val properties = testedOtelTracerProviderBuilder.properties()

        val injectionStyles = properties
            .getProperty(TracerConfig.PROPAGATION_STYLE_INJECT)
            .toString()
            .split(",")
            .toSet()
        val extractionStyles = properties
            .getProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT)
            .toString()
            .split(",")
            .toSet()

        assertThat(injectionStyles).isEqualTo(expectedDefaultPropagationStyles.map { it.headerType }.toSet())
        assertThat(extractionStyles).isEqualTo(expectedDefaultPropagationStyles.map { it.headerType }.toSet())
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
        val agentSpanContext = span.agentSpanContext as DDSpanContext
        assertThat(agentSpanContext.tags).containsEntry(key, value)
    }

    @Test
    fun `M use the internalLogger in the CoreTracer W build`() {
        // When
        val tracerProvider = testedOtelTracerProviderBuilder
            .build()
        val tracer = tracerProvider.tracerBuilder(fakeInstrumentationName).build() as OtelTracer

        // Then
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")
        val internalLogger: InternalLogger = coreTracer.getFieldValue("internalLogger")
        assertThat(internalLogger).isSameAs(mockInternalLogger)
    }

    // endregion

    // region Sampling priority

    @Test
    fun `M not add a sample rate by default W creating a tracer`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder.build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")

        // Then
        val config: Config = coreTracer.getFieldValue("initialConfig")
        val traceSampleRate: Double? = config.traceSampleRate
        assertThat(traceSampleRate).isNull()
    }

    @Test
    fun `M use the sample rate W setSampleRate`(@DoubleForgery(min = 0.0, max = 100.0) sampleRate: Double) {
        // Given
        val expectedNormalizedSampleRate = sampleRate / 100.0
        val tracer = testedOtelTracerProviderBuilder.setSampleRate(sampleRate).build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")

        // Then
        val config: Config = coreTracer.getFieldValue("initialConfig")
        assertThat(config.traceSampleRate).isCloseTo(expectedNormalizedSampleRate, offset(0.005))
    }

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
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        delegateSpan.forceSamplingDecision()
        span.end()

        // Then
        val priority = delegateSpan.samplingPriority
        assertThat(priority).isEqualTo(PrioritySampling.USER_KEEP.toInt())
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
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        delegateSpan.forceSamplingDecision()
        span.end()

        // Then
        val priority = delegateSpan.samplingPriority
        assertThat(priority).isEqualTo(PrioritySampling.USER_DROP.toInt())
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
            val delegatedSpan: DDSpan = it.getFieldValue("delegate")
            delegatedSpan.forceSamplingDecision()
            delegatedSpan
        }
        spans.forEach { it.end() }
        val droppedSpans = delegatedSpans.filter { it.samplingPriority == PrioritySampling.USER_DROP.toInt() }
        val keptSpans = delegatedSpans.filter { it.samplingPriority == PrioritySampling.USER_KEEP.toInt() }

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
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        delegateSpan.forceSamplingDecision()
        span.end()

        // Then
        val priority = delegateSpan.samplingPriority
        assertThat(priority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())
    }

    // endregion

    // region trace rate limit

    @Test
    fun `M use the trace rate limit W setTraceRateLimit`(
        @IntForgery(min = 1, max = Int.MAX_VALUE) traceRateLimit: Int
    ) {
        // Given
        val tracer = testedOtelTracerProviderBuilder.setTraceRateLimit(traceRateLimit).build()
            .tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")

        // Then
        val config: Config = coreTracer.getFieldValue("initialConfig")
        assertThat(config.traceRateLimit).isEqualTo(traceRateLimit)
    }

    @Test
    fun `M use the default rate limit W build { if not provided }`() {
        // Given
        val tracer = testedOtelTracerProviderBuilder.build().tracerBuilder(fakeInstrumentationName).build()

        // When
        val coreTracer: CoreTracer = tracer.getFieldValue("tracer")

        // Then
        val config: Config = coreTracer.getFieldValue("initialConfig")
        assertThat(config.traceRateLimit).isEqualTo(Int.MAX_VALUE)
    }

    // endregion

    // region bundle with RUM

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M build a Span with lazy Datadog context W startSpan()`(
        @Forgery fakeInitialDatadogContext: DatadogContext
    ) {
        // Given
        val tracer = testedOtelTracerProviderBuilder
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockRumFeatureScope.withContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())) doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(it.arguments.lastIndex).invoke(fakeInitialDatadogContext)
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        val context = delegateSpan.context()
        span.end()

        // Then
        assertThat(context.tags).containsKey(SpanAttributes.DATADOG_INITIAL_CONTEXT)
        val lazyContext = context.tags[SpanAttributes.DATADOG_INITIAL_CONTEXT] as CompletableFuture<DatadogContext>
        assertThat(lazyContext.value).isEqualTo(fakeInitialDatadogContext)
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
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        val context = delegateSpan.context()
        span.end()

        // Then
        assertThat(context.tags).doesNotContainKey(SpanAttributes.DATADOG_INITIAL_CONTEXT)
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
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        val context = delegateSpan.context()
        span.end()

        // Then
        assertThat(context.tags).doesNotContainKey(SpanAttributes.DATADOG_INITIAL_CONTEXT)
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
        val delegatedTracer: AgentTracer.TracerAPI = tracer.getFieldValue("tracer")
        val scopeManager: AgentScopeManager = delegatedTracer.getFieldValue("scopeManager")
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DDSpan = span.getFieldValue("delegate")
        val expectedTraceId = delegateSpan.context().traceId.toHexString()
        val expectedSpanId = delegateSpan.context().spanId.toString()

        // When
        val scope = scopeManager.activate(delegateSpan, ScopeSource.INSTRUMENTATION)
        scope.close()
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
        val tracer = testedOtelTracerProviderBuilder
            .build()
            .tracerBuilder(fakeInstrumentationName)
            .build()
        val delegatedTracer: AgentTracer.TracerAPI = tracer.getFieldValue("tracer")
        val scopeManager: AgentScopeManager = spy(delegatedTracer.getFieldValue("scopeManager")) {
            whenever(it.activeSpan()).thenReturn(null)
        }
        delegatedTracer.setFieldValue("scopeManager", scopeManager)
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        val delegateSpan: DDSpan = span.getFieldValue("delegate")

        // When
        val scope = scopeManager.activate(delegateSpan, ScopeSource.INSTRUMENTATION)
        scope.close()
        span.end()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            val traceContext: MutableMap<String, Any?> = mutableMapOf()
            verify(mockSdkCore, times(2))
                .updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), capture())
            lastValue.invoke(traceContext)
            assertThat(traceContext).doesNotContainKey(expectedActiveTraceContextName)
        }
    }

    // endregion

    // region Tracing context

    @Test
    fun `M set OpenTelemetry as enabled in Context W build { TracingFeature enabled }`() {
        // WHEN
        testedOtelTracerProviderBuilder.build()

        // THEN
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            val traceContext: MutableMap<String, Any?> = mutableMapOf()
            verify(mockSdkCore, times(1))
                .updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), eq(false), capture())
            lastValue.invoke(traceContext)
            assertThat(traceContext[OtelTracerProvider.IS_OPENTELEMETRY_ENABLED_CONFIG_KEY]).isEqualTo(true)
            assertThat(traceContext[OtelTracerProvider.OPENTELEMETRY_API_VERSION_CONFIG_KEY])
                .isEqualTo(BuildConfig.OPENTELEMETRY_API_VERSION_NAME)
        }
    }

    @Test
    fun `M not update the Context W build { TracingFeature not enabled }`() {
        // WHEN
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null
        testedOtelTracerProviderBuilder.build()

        // THEN
        verify(mockSdkCore, never()).updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), any())
    }

    // endregion

    class StubTracingFeature : Feature, InternalCoreWriterProvider {
        override val name: String
            get() = ""

        override fun onInitialize(appContext: Context) {
        }

        override fun onStop() {
        }

        override fun getCoreTracerWriter(): Writer {
            return mock()
        }
    }

    companion object {

        val forge = Forge()

        @JvmStatic
        fun brokenRumContextProvider(): List<Map<String, String>> {
            return listOf(
                mapOf(),
                mapOf(
                    OtelTracerProvider.RUM_SESSION_ID_KEY to forge.anAlphabeticalString(),
                    OtelTracerProvider.RUM_VIEW_ID_KEY to forge.anAlphabeticalString(),
                    OtelTracerProvider.RUM_ACTION_ID_KEY to forge.anAlphabeticalString()
                ),
                mapOf(
                    OtelTracerProvider.RUM_APPLICATION_ID_KEY to forge.anAlphabeticalString(),
                    OtelTracerProvider.RUM_VIEW_ID_KEY to forge.anAlphabeticalString(),
                    OtelTracerProvider.RUM_ACTION_ID_KEY to forge.anAlphabeticalString()
                ),
                mapOf(
                    OtelTracerProvider.RUM_APPLICATION_ID_KEY to forge.anAlphabeticalString(),
                    OtelTracerProvider.RUM_SESSION_ID_KEY to forge.anAlphabeticalString(),
                    OtelTracerProvider.RUM_ACTION_ID_KEY to forge.anAlphabeticalString()
                ),
                mapOf(
                    OtelTracerProvider.RUM_ACTION_ID_KEY to forge.anAlphabeticalString()
                )
            )
        }
    }
}
