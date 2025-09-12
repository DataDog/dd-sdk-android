/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.otel

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.tests.ktx.getInt
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.integration.tests.assertj.SpansPayloadAssert
import com.datadog.android.trace.integration.tests.elmyr.TraceIntegrationForgeConfigurator
import com.datadog.android.trace.integration.tests.utils.BlockingWriterWrapper
import com.datadog.android.trace.opentelemetry.OtelTracerProvider
import com.datadog.opentelemetry.trace.OtelConventions
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.bootstrap.instrumentation.api.Tags
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(TraceIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class OtelTracerProviderTest {

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var blockingWriterWrapper: BlockingWriterWrapper

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
        blockingWriterWrapper = checkNotNull(stubSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)).useBlockingWriter()
    }

    // region Span attributes

    @RepeatedTest(10)
    fun `M send trace with custom service W setService() + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        @StringForgery fakeService: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setService(fakeService).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(fakeService)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
    }

    @RepeatedTest(10)
    fun `M send trace with global tag service W addTag() + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        @StringForgery fakeGlobalTagKey: String,
        @StringForgery fakeGlobalTagValue: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore)
            .addTag(fakeGlobalTagKey, fakeGlobalTagValue)
            .build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue(fakeGlobalTagKey, fakeGlobalTagValue)
            }
    }

    @RepeatedTest(10)
    fun `M send trace with attributes W setAttribute() + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        forge: Forge
    ) {
        // Given
        val fakeLongAttributeKey = forge.anAlphabeticalString()
        val fakeLongAttributeValue = forge.aLong()
        val fakeDoubleAttributeKey = forge.anAlphabeticalString()
        val fakeDoubleAttributeValue = forge.aDouble()
        val fakeStringAttributeKey = forge.anAlphabeticalString()
        val fakeStringAttributeValue = forge.anAlphabeticalString()
        val fakeBooleanAttributeKey = forge.anAlphabeticalString()
        val fakeBooleanAttributeValue = forge.aBool()
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName)
                .setAttribute(fakeLongAttributeKey, fakeLongAttributeValue)
                .setAttribute(fakeDoubleAttributeKey, fakeDoubleAttributeValue)
                .setAttribute(fakeStringAttributeKey, fakeStringAttributeValue)
                .setAttribute(fakeBooleanAttributeKey, fakeBooleanAttributeValue)
                .startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasGenericMetricValue(fakeLongAttributeKey, fakeLongAttributeValue)
                hasGenericMetricValue(fakeDoubleAttributeKey, fakeDoubleAttributeValue)
                hasGenericMetaValue(fakeStringAttributeKey, fakeStringAttributeValue)
                hasGenericMetaValue(fakeBooleanAttributeKey, fakeBooleanAttributeValue.toString())
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
    }

    @RepeatedTest(10)
    fun `M send a trace W attributes setAttributeWithKey() + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        forge: Forge
    ) {
        // Given
        val fakeLongAttributeKey = forge.anAlphabeticalString()
        val fakeLongAttributeValue = forge.aLong()
        val fakeDoubleAttributeKey = forge.anAlphabeticalString()
        val fakeDoubleAttributeValue = forge.aDouble()
        val fakeStringAttributeKey = forge.anAlphabeticalString()
        val fakeStringAttributeValue = forge.anAlphabeticalString()
        val fakeBooleanAttributeKey = forge.anAlphabeticalString()
        val fakeBooleanAttributeValue = forge.aBool()
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName)
                .setAttribute(AttributeKey.longKey(fakeLongAttributeKey), fakeLongAttributeValue)
                .setAttribute(AttributeKey.doubleKey(fakeDoubleAttributeKey), fakeDoubleAttributeValue)
                .setAttribute(AttributeKey.stringKey(fakeStringAttributeKey), fakeStringAttributeValue)
                .setAttribute(AttributeKey.booleanKey(fakeBooleanAttributeKey), fakeBooleanAttributeValue)
                .startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasGenericMetricValue(fakeLongAttributeKey, fakeLongAttributeValue)
                hasGenericMetricValue(fakeDoubleAttributeKey, fakeDoubleAttributeValue)
                hasGenericMetaValue(fakeStringAttributeKey, fakeStringAttributeValue)
                hasGenericMetaValue(fakeBooleanAttributeKey, fakeBooleanAttributeValue.toString())
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
    }

    // endregion

    // region Span parent

    @RepeatedTest(10)
    fun `M send trace with parent span W setParent() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        @StringForgery fakeParentSpanName: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()
        var leastSignificantParentSpanTraceId: String
        var mostSignficantParentSpanTraceId: String
        var parentSpanId: String
        var spanId: String
        var fullChildDuration: Long

        // When
        val fullParentSpanDuration = measureNanoTime {
            val parenSpan = tracer.spanBuilder(fakeParentSpanName).startSpan()
            leastSignificantParentSpanTraceId = parenSpan.leastSignificant64BitsTraceIdAsHex()
            mostSignficantParentSpanTraceId = parenSpan.mostSignificant64BitsTraceIdAsHex()
            parentSpanId = parenSpan.spanIdAsHex()
            fullChildDuration = measureNanoTime {
                val span = tracer
                    .spanBuilder(fakeOperationName)
                    .setParent(Context.current().with(parenSpan))
                    .startSpan()
                spanId = span.spanIdAsHex()
                Thread.sleep(OP_DURATION_MS)
                span.end()
            }
            Thread.sleep(OP_DURATION_MS)
            parenSpan.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        val payload1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantParentSpanTraceId)
                hasMostSignificant64BitsTraceId(mostSignficantParentSpanTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(parentSpanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeParentSpanName)
                hasDurationBetween(OP_DURATION_NS * 2, fullParentSpanDuration)
            }
        SpansPayloadAssert.assertThat(payload1)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantParentSpanTraceId)
                hasMostSignificant64BitsTraceId(mostSignficantParentSpanTraceId)
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullChildDuration)
                hasParentId(parentSpanId)
            }
    }

    // endregion

    // region Span kind

    @RepeatedTest(10)
    fun `M send a trace W spanKind as operationName setSpanKind() + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        forge: Forge
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()
        val spanKind = forge.aValueFrom(SpanKind::class.java)

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        var span: Span
        val fullDuration = measureNanoTime {
            span = tracer.spanBuilder(fakeOperationName)
                .setSpanKind(spanKind)
                .startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }
        // because the span kind is having a direct impact on the operation name we will have to resolve
        // the expected span name from the delegated AgentSpan
        val expectedSpanName = span.expectedSpanName()
        val expectedTagName = OtelConventions.toSpanKindTagValue(spanKind)

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(expectedSpanName)
                hasGenericMetaValue(Tags.SPAN_KIND, expectedTagName)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
    }

    // endregion

    // region Span startTimestamp

    @RepeatedTest(10)
    fun `M send a trace W a custom startTime setStartTimestamp + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        forge: Forge
    ) {
        // Given
        val fakeStartTimestamp = System.currentTimeMillis() - forge.aPositiveLong()
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        var span: Span
        val fullDuration = measureNanoTime {
            span = tracer.spanBuilder(fakeOperationName)
                .setStartTimestamp(Instant.ofEpochMilli(fakeStartTimestamp))
                .startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeOperationName)
                hasDurationBetween(fakeStartTimestamp, fullDuration)
            }
    }

    // endregion

    // region Span link

    @RepeatedTest(10)
    fun `M send a trace with a SpanLink W addLink() + buildSpan() + startSpan() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        @StringForgery fakeLinkSpanName: String,
        forge: Forge
    ) {
        // Given
        val fakeLongKey = forge.anAlphabeticalString()
        val fakeLongAttribute = forge.aLong()
        val fakeStringKey = forge.anAlphabeticalString()
        val fakeStringAttribute = forge.aString()
        val fakeStringArrayKey = forge.anAlphabeticalString()
        val fakeStringArray = forge.aList { forge.aString() }
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()
        val attributes = Attributes.builder()
            .put(fakeStringKey, fakeStringAttribute)
            .put(fakeLongKey, fakeLongAttribute)
            .put(fakeStringArrayKey, *fakeStringArray.toTypedArray())
            .build()

        // When
        val linkedSpan = tracer.spanBuilder(fakeLinkSpanName)
            .startSpan()
        val linkedSpanTraceId = linkedSpan.spanContext.traceId
        val linkedSpanId = linkedSpan.spanIdAsHex()
        Thread.sleep(OP_DURATION_MS)
        linkedSpan.end()
        val span = tracer.spanBuilder(fakeOperationName)
            .addLink(linkedSpan.spanContext, attributes)
            .startSpan()
        val leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
        val mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
        val spanId = span.spanIdAsHex()
        Thread.sleep(OP_DURATION_MS)
        span.end()

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val payload1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload1)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasLinkedSpanId(linkedSpanId)
                hasLinkedTraceId(linkedSpanTraceId)
                hasGenericLinkedAttribute(fakeStringKey, fakeStringAttribute)
                hasGenericLinkedAttribute(fakeLongKey, fakeLongAttribute)
                hasGenericLinkedAttribute(fakeStringArrayKey, fakeStringArray)
                hasError(0)
                hasResource(fakeOperationName)
            }
    }

    // endregion

    // region Scopes

    @RepeatedTest(10)
    fun `M send an entire scoped trace W startSpan() + makeCurrent() + startSpan() + end() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeParentSpanName: String,
        @StringForgery fakeSpanName: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val parentSpan = tracer.spanBuilder(fakeParentSpanName).startSpan()
        val scope = parentSpan.makeCurrent()
        val parentSpanLeastSignificantTraceId = parentSpan.leastSignificant64BitsTraceIdAsHex()
        val parentSpanMostSignificantTraceId = parentSpan.mostSignificant64BitsTraceIdAsHex()
        val parentSpanId = parentSpan.spanIdAsHex()
        val childSpan = tracer.spanBuilder(fakeSpanName).startSpan()
        val childSpanId = childSpan.spanIdAsHex()
        Thread.sleep(OP_DURATION_MS)
        childSpan.end()
        Thread.sleep(OP_DURATION_MS)
        scope.close()
        parentSpan.end()

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        val payload1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload1)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(parentSpanLeastSignificantTraceId)
                hasMostSignificant64BitsTraceId(parentSpanMostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(childSpanId)
                hasParentId(parentSpanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeSpanName)
            }
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(parentSpanLeastSignificantTraceId)
                hasMostSignificant64BitsTraceId(parentSpanMostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(parentSpanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeParentSpanName)
            }
    }

    @RepeatedTest(10)
    fun `M send an entire scoped trace W startSpan() + newThread() + makeCurrent() + startSpan() + end() + end()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeParentSpanName: String,
        @StringForgery fakeSpanName: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val parentSpan = tracer.spanBuilder(fakeParentSpanName).startSpan()
        val scope = parentSpan.makeCurrent()
        val parentSpanLeastSignificantTraceId = parentSpan.leastSignificant64BitsTraceIdAsHex()
        val parentSpanMostSignificantTraceId = parentSpan.mostSignificant64BitsTraceIdAsHex()
        val parentSpanId = parentSpan.spanIdAsHex()
        var childSpanId = ""
        Thread {
            val childSpan = tracer.spanBuilder(fakeSpanName)
                .setParent(Context.current().with(parentSpan))
                .startSpan()
            childSpanId = childSpan.spanIdAsHex()
            childSpan.end()
        }.apply {
            start()
            join(JOIN_TIMEOUT_MS)
        }
        scope.close()
        parentSpan.end()

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        val payload1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload1)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(parentSpanLeastSignificantTraceId)
                hasMostSignificant64BitsTraceId(parentSpanMostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(childSpanId)
                hasParentId(parentSpanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeSpanName)
            }
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(parentSpanLeastSignificantTraceId)
                hasMostSignificant64BitsTraceId(parentSpanMostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(parentSpanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeParentSpanName)
            }
    }

    // endregion

    // region Sampling priority

    @RepeatedTest(10)
    fun `M use user-keep priority W buildSpan { tracer uses keep sample rate }`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setSampleRate(100.0).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        Thread.sleep(OP_DURATION_MS)
        val leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
        val mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
        val spanId = span.spanIdAsHex()
        span.end()

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeOperationName)
                hasSamplingPriority(PrioritySampling.USER_KEEP.toInt())
                hasName(DEFAULT_SPAN_NAME)
            }
    }

    @RepeatedTest(10)
    fun `M not write spans W buildSpan { tracer uses drop sample rate }`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setSampleRate(0.0).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        Thread.sleep(OP_DURATION_MS)
        span.end()

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).isEmpty()
    }

    @RepeatedTest(10)
    fun `M use auto-keep priority W buildSpan { tracer was not provided a sample rate }`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val span = tracer
            .spanBuilder(fakeOperationName)
            .startSpan()
        Thread.sleep(OP_DURATION_MS)
        val leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
        val mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
        val spanId = span.spanIdAsHex()
        span.end()

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasResource(fakeOperationName)
                hasSamplingPriority(PrioritySampling.SAMPLER_KEEP.toInt())
                hasName(DEFAULT_SPAN_NAME)
            }
    }

    @Test
    fun `M use user-keep or user-drop priority W buildSpan { tracer was provided a sample rate }`(
        @StringForgery fakeInstrumentationName: String,
        @DoubleForgery(min = 0.0, max = 100.0) sampleRate: Double,
        forge: Forge
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setSampleRate(sampleRate).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()
        val numberOfSpans = 300
        val normalizedSampleRate = sampleRate / 100.0
        val expectedKeptSpans = (numberOfSpans * normalizedSampleRate).toInt()

        // When
        repeat(numberOfSpans) {
            tracer.spanBuilder(forge.anAlphabeticalString())
                .startSpan()
                .end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(numberOfSpans)
        val spansWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
            .map {
                (JsonParser.parseString(it.eventData) as JsonObject)
                    .getAsJsonArray("spans")
                    .get(0)
                    .asJsonObject
            }
        val droppedSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_DROP.toInt()
        }
        val keptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_KEEP.toInt()
        }
        // Because of the way sampling works the deviation can be quite high so we will have to use an offset of 15%
        // here to make sure this test will never be flaky
        val offset = numberOfSpans * 15 / 100
        assertThat(droppedSpans).isEmpty()
        assertThat(keptSpans.size).isCloseTo(expectedKeptSpans, Offset.offset(offset))
    }

    // endregion

    // region trace rate limit

    @Test
    fun `M drop the spans W buildSpan { trace rate limit reached in 1 second, sample rate specified }`(
        @StringForgery fakeInstrumentationName: String,
        @IntForgery(min = 1, max = 3) traceLimit: Int,
        forge: Forge
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore)
            .setTraceRateLimit(traceLimit)
            .setSampleRate(100.0)
            .build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val startNanos = System.nanoTime()
        var spansCounter = 0
        while ((System.nanoTime() - startNanos) < ONE_SECOND_AS_NANOS && (spansCounter < 200)) {
            tracer.spanBuilder(forge.anAlphabeticalString()).startSpan().end()
            spansCounter++
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(spansCounter)
        val spansWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
            .map {
                (JsonParser.parseString(it.eventData) as JsonObject)
                    .getAsJsonArray("spans")
                    .get(0)
                    .asJsonObject
            }
        val userKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_KEEP.toInt()
        }
        val samplerKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.SAMPLER_KEEP.toInt()
        }
        assertThat(samplerKeptSpans.size).isEqualTo(0)
        assertThat(userKeptSpans.size).isLessThanOrEqualTo(traceLimit)
    }

    @Test
    fun `M ignore trace rate limit W buildSpan { trace rate limit reached in 1 second, sample rate not specified }`(
        @StringForgery fakeInstrumentationName: String,
        @IntForgery(min = 1, max = 3) traceLimit: Int,
        forge: Forge
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore)
            .setTraceRateLimit(traceLimit)
            .build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val startNanos = System.nanoTime()
        var spansCounter = 0
        while ((System.nanoTime() - startNanos) < ONE_SECOND_AS_NANOS && (spansCounter < 200)) {
            tracer.spanBuilder(forge.anAlphabeticalString()).startSpan().end()
            spansCounter++
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(spansCounter)
        val spansWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
            .map {
                (JsonParser.parseString(it.eventData) as JsonObject)
                    .getAsJsonArray("spans")
                    .get(0)
                    .asJsonObject
            }
        val userKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_KEEP.toInt()
        }
        val samplerKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.SAMPLER_KEEP.toInt()
        }
        assertThat(samplerKeptSpans.size).isEqualTo(spansCounter)
        assertThat(userKeptSpans.size).isEqualTo(0)
    }

    @Test
    fun `M ignore trace limit W buildSpan { trace rate limit is 1 but sample rate is not specified }`(
        @StringForgery fakeInstrumentationName: String,
        forge: Forge
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setTraceRateLimit(1).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        val startNanos = System.nanoTime()
        var spansCounter = 0
        while (((System.nanoTime() - startNanos) < (ONE_SECOND_AS_NANOS * 2)) && (spansCounter < 200)) {
            tracer.spanBuilder(forge.anAlphabeticalString()).startSpan().end()
            spansCounter++
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(spansCounter)
        val spansWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
            .map {
                (JsonParser.parseString(it.eventData) as JsonObject)
                    .getAsJsonArray("spans")
                    .get(0)
                    .asJsonObject
            }
        val userDroppedSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_DROP.toInt()
        }
        val samplerDroppedSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.SAMPLER_DROP.toInt()
        }
        val userKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_KEEP.toInt()
        }
        val samplerKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.SAMPLER_KEEP.toInt()
        }
        assertThat(userDroppedSpans.size).isEqualTo(0)
        assertThat(samplerDroppedSpans.size).isEqualTo(0)
        assertThat(userKeptSpans.size).isEqualTo(0)
        assertThat(samplerKeptSpans.size).isEqualTo(spansCounter)
    }

    @Test
    fun `M only keep 1 span W buildSpan { trace rate limit is 1 and sample rate is specified }`(
        @StringForgery fakeInstrumentationName: String,
        @IntForgery(min = 2, max = 10) numberOfSpans: Int,
        forge: Forge
    ) {
        // Given
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore)
            .setTraceRateLimit(1)
            .setSampleRate(100.0)
            .build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        repeat(numberOfSpans) {
            tracer.spanBuilder(forge.anAlphabeticalString()).startSpan().end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(numberOfSpans)
        val spansWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
            .map {
                (JsonParser.parseString(it.eventData) as JsonObject)
                    .getAsJsonArray("spans")
                    .get(0)
                    .asJsonObject
            }
        val userDroppedSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_DROP.toInt()
        }
        val samplerDroppedSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.SAMPLER_DROP.toInt()
        }
        val userKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.USER_KEEP.toInt()
        }
        val samplerKeptSpans = spansWritten.filter {
            it.getInt(SAMPLING_PRIORITY_KEY) == PrioritySampling.SAMPLER_KEEP.toInt()
        }

        assertThat(samplerDroppedSpans).isEmpty()
        assertThat(userKeptSpans.size).isEqualTo(1)
        assertThat(samplerKeptSpans).isEmpty()
        assertThat(userDroppedSpans).isEmpty()
    }

    // endregion

    // region Bundle with RUM

    @RepeatedTest(10)
    fun `M send span with rum context W setBundleWithRumEnabled(true) + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeRumApplicationId: String,
        @StringForgery fakeRumSessionId: String,
        @StringForgery fakeRumViewId: String,
        @StringForgery fakeRumActionId: String,
        @StringForgery fakeOperationName: String
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.RUM_FEATURE_NAME)
        stubSdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it["application_id"] = fakeRumApplicationId
            it["session_id"] = fakeRumSessionId
            it["view_id"] = fakeRumViewId
            it["action_id"] = fakeRumActionId
        }
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasApplicationId(fakeRumApplicationId)
                hasSessionId(fakeRumSessionId)
                hasViewId(fakeRumViewId)
            }
    }

    // endregion

    // region User info

    @RepeatedTest(10)
    fun `M send trace with base user info W SDKCore#setUserInfo() + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        @StringForgery fakeUserId: String,
        @StringForgery fakeUserName: String,
        @StringForgery fakeUserEmail: String
    ) {
        // Given
        stubSdkCore.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail)
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasUserId(fakeUserId)
                hasUserName(fakeUserName)
                hasUserEmail(fakeUserEmail)
            }
    }

    @RepeatedTest(10)
    fun `M send trace with custom user info W SDKCore#setUserInfo() + buildSpan() + start() + finish()`(
        @StringForgery fakeUserId: String,
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperation: String,
        @StringForgery fakeUserKey: String,
        @StringForgery fakeUserValue: String
    ) {
        // Given
        stubSdkCore.setUserInfo(id = fakeUserId, extraInfo = mapOf(fakeUserKey to fakeUserValue))
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperation).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue("usr.$fakeUserKey", fakeUserValue)
            }
    }

    @Test
    fun `M send trace with base account info W SDKCore#setAccountInfo() + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperationName: String,
        @StringForgery fakeAccountId: String,
        @StringForgery fakeAccountName: String
    ) {
        // Given
        stubSdkCore.setAccountInfo(fakeAccountId, fakeAccountName)
        val testedProvider =
            OtelTracerProvider.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperationName).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperationName)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue("account.id", fakeAccountId)
                hasGenericMetaValue("account.name", fakeAccountName)
            }
    }

    @Test
    fun `M send trace with custom account info W SDKCore#setAccountInfo() + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperation: String,
        @StringForgery fakeAccountId: String,
        @StringForgery fakeAccountKey: String,
        @StringForgery fakeAccountValue: String
    ) {
        // Given
        stubSdkCore.setAccountInfo(
            id = fakeAccountId,
            extraInfo = mapOf(fakeAccountKey to fakeAccountValue)
        )
        val testedProvider =
            OtelTracerProvider.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperation).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
                hasService(stubSdkCore.getDatadogContext().service)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue("account.$fakeAccountKey", fakeAccountValue)
            }
    }

    // endregion

    companion object {
        private val ONE_SECOND_AS_NANOS = TimeUnit.SECONDS.toNanos(1)
        private const val DEFAULT_SPAN_NAME = "internal"
        private const val JOIN_TIMEOUT_MS = 5000L
        private const val SAMPLING_PRIORITY_KEY = "metrics._sampling_priority_v1"
        private const val OP_DURATION_MS = 10L
        private val OP_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(OP_DURATION_MS)
    }
}
