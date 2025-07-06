/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.opentracing

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.tests.ktx.getInt
import com.datadog.android.tests.ktx.getLong
import com.datadog.android.tests.ktx.getString
import com.datadog.android.trace.GlobalDatadogTracerHolder
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.api.span.clear
import com.datadog.android.trace.api.span.setSpanLoggerMock
import com.datadog.android.trace.impl.DatadogTracing
import com.datadog.android.trace.impl.internal.DatadogTracingInternal
import com.datadog.android.trace.integration.tests.elmyr.TraceIntegrationForgeConfigurator
import com.datadog.android.trace.logErrorMessage
import com.datadog.android.trace.logThrowable
import com.datadog.android.trace.withinSpan
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(TraceIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UtilitiesTest {

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        DatadogTracing.clear()
        GlobalDatadogTracerHolder.clear()
    }

    @RepeatedTest(16)
    fun `M send span with exception W buildSpan() + start() + setError(Throwable) + finish()`(
        @StringForgery fakeOperation: String,
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.LOGS_FEATURE_NAME)
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()

        // When
        var traceId: String
        val mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            DatadogTracing.spanLogger.log(fakeThrowable, span)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId)
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(1)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
        assertThat(event0.getString("spans[0].meta.error.type")).isEqualTo(fakeThrowable.javaClass.canonicalName)
        assertThat(event0.getString("spans[0].meta.error.msg")).isEqualTo(fakeThrowable.message)
        assertThat(event0.getString("spans[0].meta.error.stack")).isEqualTo(fakeThrowable.stackTraceToString())
        val eventsReceived = stubSdkCore.eventsReceived(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsReceived).isEmpty()
    }

    @RepeatedTest(16)
    @Suppress("UNCHECKED_CAST")
    fun `M send span and log W buildSpan() + start() + setError(String) + finish()`(
        @StringForgery fakeOperation: String,
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.LOGS_FEATURE_NAME)
        DatadogTracing.setSpanLoggerMock(stubSdkCore)
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()

        // When
        var leastSignificantTraceId: String
        val mostSignificantTraceId: String
        var traceId: String
        var spanId: Long
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            traceId = span.traceId.toHexString()
            spanId = span.context().spanId
            Thread.sleep(OP_DURATION_MS)
            DatadogTracing.spanLogger.logErrorMessage(fakeErrorMessage, span)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(leastSignificantTraceId)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId)
        assertThat(
            event0.getString("spans[0].span_id")
        ).isEqualTo(DatadogTracingInternal.spanIdConverter.toHexStringPadded(spanId))
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(0)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
        assertThat(event0.getString("spans[0].meta.error.msg")).isNull()

        val eventsReceived = stubSdkCore.eventsReceived(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsReceived).hasSize(1)
        val logEvent0: Map<String, Any?> = eventsReceived[0] as Map<String, Any?>
        assertThat(logEvent0).containsEntry("type", "span_log")
        assertThat(logEvent0).containsEntry("loggerName", "trace")
        assertThat(logEvent0).containsEntry("message", fakeErrorMessage)
        assertThat(logEvent0["attributes"] as? Map<String, Any?>).containsEntry("dd.trace_id", traceId)
        assertThat(logEvent0["attributes"] as? Map<String, Any?>).containsEntry("dd.span_id", spanId.toString())
    }

    @RepeatedTest(16)
    fun `M send span with exception W buildSpan() + start() + logThrowable () + finish()`(
        @StringForgery fakeOperation: String,
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.LOGS_FEATURE_NAME)
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()

        // When
        val mostSignificantTraceId: String
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            Thread.sleep(OP_DURATION_MS)
            traceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            span.logThrowable(fakeThrowable)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId)
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(1)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
        assertThat(event0.getString("spans[0].meta.error.type")).isEqualTo(fakeThrowable.javaClass.canonicalName)
        assertThat(event0.getString("spans[0].meta.error.msg")).isEqualTo(fakeThrowable.message)
        assertThat(event0.getString("spans[0].meta.error.stack")).isEqualTo(fakeThrowable.stackTraceToString())
        val eventsReceived = stubSdkCore.eventsReceived(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsReceived).isEmpty()
    }

    @RepeatedTest(16)
    @Suppress("UNCHECKED_CAST")
    fun `M send span and log W buildSpan() + start() + logErrorMessage() + finish()`(
        @StringForgery fakeOperation: String,
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.LOGS_FEATURE_NAME)
        DatadogTracing.setSpanLoggerMock(stubSdkCore)
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()

        // When
        var leastSignificantTraceId: String
        val traceId: String
        val mostSignificantTraceId: String
        var spanId: Long
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            traceId = span.traceId.toHexString()
            spanId = span.context().spanId
            Thread.sleep(OP_DURATION_MS)
            span.logErrorMessage(fakeErrorMessage)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(leastSignificantTraceId)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId)
        assertThat(
            event0.getString("spans[0].span_id")
        ).isEqualTo(
            DatadogTracingInternal.spanIdConverter.toHexStringPadded(spanId)
        )
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(0)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
        assertThat(event0.getString("spans[0].meta.error.msg")).isNull()
        val eventsReceived = stubSdkCore.eventsReceived(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsReceived).hasSize(1)
        val logEvent0: Map<String, Any?> = eventsReceived[0] as Map<String, Any?>
        assertThat(logEvent0).containsEntry("type", "span_log")
        assertThat(logEvent0).containsEntry("loggerName", "trace")
        assertThat(logEvent0).containsEntry("message", fakeErrorMessage)
        assertThat(logEvent0["attributes"] as? Map<String, Any?>).containsEntry("dd.trace_id", traceId)
        assertThat(logEvent0["attributes"] as? Map<String, Any?>).containsEntry("dd.span_id", spanId.toString())
    }

    @RepeatedTest(16)
    fun `M send span W withinSpan()`(
        @StringForgery fakeOperation: String
    ) {
        // Given
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()
        GlobalDatadogTracerHolder.registerIfAbsent(testedTracer)

        // When
        var traceId = ""
        var spanId = ""
        var mostSignificantTraceId = ""
        val fullDuration = measureNanoTime {
            withinSpan(fakeOperation) {
                traceId = leastSignificant64BitsTraceId()
                mostSignificantTraceId = mostSignificant64BitsTraceId()
                spanId = spanIdAsHexString()
                Thread.sleep(OP_DURATION_MS)
            }
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId)
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(0)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
    }

    @RepeatedTest(16)
    fun `M send nested span W withinSpan() { nested lambdas }`(
        @StringForgery fakeOperation0: String,
        @StringForgery fakeOperation1: String,
        @StringForgery fakeOperation2: String
    ) {
        // Given
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()
        GlobalDatadogTracerHolder.registerIfAbsent(testedTracer)

        // When
        var traceId0 = ""
        var traceId1 = ""
        var traceId2 = ""
        var spanId0 = ""
        var spanId1 = ""
        var spanId2 = ""
        var mostSignificantTraceId0 = ""
        var mostSignificantTraceId1 = ""
        var mostSignificantTraceId2 = ""
        withinSpan(fakeOperation0) {
            traceId0 = leastSignificant64BitsTraceId()
            mostSignificantTraceId0 = mostSignificant64BitsTraceId()
            spanId0 = spanIdAsHexString()
            withinSpan(fakeOperation1) {
                traceId1 = leastSignificant64BitsTraceId()
                mostSignificantTraceId1 = mostSignificant64BitsTraceId()
                spanId1 = spanIdAsHexString()
                withinSpan(fakeOperation2) {
                    traceId2 = leastSignificant64BitsTraceId()
                    mostSignificantTraceId2 = mostSignificant64BitsTraceId()
                    spanId2 = spanIdAsHexString()
                    Thread.sleep(OP_DURATION_MS)
                }
            }
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(3)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        val event1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        val event2 = JsonParser.parseString(eventsWritten[2].eventData) as JsonObject
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId0)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId0)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId0)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation0)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation0)
        assertThat(event1.getString("spans[0].trace_id")).isEqualTo(traceId1)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId1)
        assertThat(event1.getString("spans[0].span_id")).isEqualTo(spanId1)
        assertThat(event1.getString("spans[0].name")).isEqualTo(fakeOperation1)
        assertThat(event1.getString("spans[0].resource")).isEqualTo(fakeOperation1)
        assertThat(event2.getString("spans[0].trace_id")).isEqualTo(traceId2)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId2)
        assertThat(event2.getString("spans[0].span_id")).isEqualTo(spanId2)
        assertThat(event2.getString("spans[0].name")).isEqualTo(fakeOperation2)
        assertThat(event2.getString("spans[0].resource")).isEqualTo(fakeOperation2)
    }

    @RepeatedTest(16)
    fun `M send span with exception W withinSpan() + throw`(
        @StringForgery fakeOperation: String,
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val testedTracer = DatadogTracing.newTracerBuilder(stubSdkCore).build()
        GlobalDatadogTracerHolder.registerIfAbsent(testedTracer)

        // When
        var traceId = ""
        var mostSignificantTraceId = ""
        var spanId = ""
        var thrown: Throwable? = null
        val fullDuration = measureNanoTime {
            try {
                withinSpan(fakeOperation) {
                    traceId = leastSignificant64BitsTraceId()
                    mostSignificantTraceId = mostSignificant64BitsTraceId()
                    spanId = spanIdAsHexString()
                    Thread.sleep(OP_DURATION_MS)
                    throw fakeThrowable
                }
            } catch (e: Throwable) {
                thrown = e
            }
        }

        // Then
        assertThat(thrown).isSameAs(fakeThrowable)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
        assertThat(event0.getString("spans[0].meta._dd.p.id")).isEqualTo(mostSignificantTraceId)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId)
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(1)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeOperation)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
        assertThat(event0.getString("spans[0].meta.error.type")).isEqualTo(fakeThrowable.javaClass.canonicalName)
        assertThat(event0.getString("spans[0].meta.error.msg")).isEqualTo(fakeThrowable.message)
        assertThat(event0.getString("spans[0].meta.error.stack")).isEqualTo(fakeThrowable.stackTraceToString())
    }

    companion object {
        const val OP_DURATION_MS = 10L
        val OP_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(OP_DURATION_MS)
    }
}
