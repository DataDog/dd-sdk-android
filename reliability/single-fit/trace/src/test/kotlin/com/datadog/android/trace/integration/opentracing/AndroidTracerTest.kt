/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.opentracing

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.tests.ktx.getDouble
import com.datadog.android.tests.ktx.getInt
import com.datadog.android.tests.ktx.getLong
import com.datadog.android.tests.ktx.getString
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.integration.tests.elmyr.TraceIntegrationForgeConfigurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.setStaticValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.assertj.core.data.Offset
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
class AndroidTracerTest {

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @RepeatedTest(16)
    fun `M send trace with custom service W setService() + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String,
        @StringForgery fakeService: String
    ) {
        // Given
        val testedTracer = AndroidTracer.Builder(stubSdkCore).setService(fakeService).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId)
        assertThat(event0.getString("spans[0].service")).isEqualTo(fakeService)
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
    fun `M send span with tag W addTag() + buildSpan() + start() + finish()`(
        @StringForgery fakeTagKey: String,
        @StringForgery fakeTagValue: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        val testedTracer = AndroidTracer.Builder(stubSdkCore).addTag(fakeTagKey, fakeTagValue).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
        assertThat(event0.getString("spans[0].meta.$fakeTagKey")).isEqualTo(fakeTagValue)
    }

    @RepeatedTest(16)
    @Suppress("DEPRECATION")
    fun `M send span with global tag W addGlobalTag() + buildSpan() + start() + finish()`(
        @StringForgery fakeTagKey: String,
        @StringForgery fakeTagValue: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        val testedTracer = AndroidTracer.Builder(stubSdkCore).addGlobalTag(fakeTagKey, fakeTagValue).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
        assertThat(event0.getString("spans[0].meta.$fakeTagKey")).isEqualTo(fakeTagValue)
    }

    @RepeatedTest(16)
    fun `M send span without rum context W setBundleWithRumEnabled(false) + buildSpan() + start() + finish()`(
        @StringForgery fakeRumApplicationId: String,
        @StringForgery fakeRumSessionId: String,
        @StringForgery fakeRumViewId: String,
        @StringForgery fakeRumActionId: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.RUM_FEATURE_NAME)
        stubSdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it["application_id"] = fakeRumApplicationId
            it["session_id"] = fakeRumSessionId
            it["view_id"] = fakeRumViewId
            it["action_id"] = fakeRumActionId
        }
        val testedTracer = AndroidTracer.Builder(stubSdkCore).setBundleWithRumEnabled(false).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
        assertThat(event0.getString("spans[0].meta._dd.application.id")).isNull()
        assertThat(event0.getString("spans[0].meta._dd.session.id")).isNull()
        assertThat(event0.getString("spans[0].meta._dd.view.id")).isNull()
    }

    @RepeatedTest(16)
    fun `M send span with rum context W setBundleWithRumEnabled(true) + buildSpan() + start() + finish()`(
        @StringForgery fakeRumApplicationId: String,
        @StringForgery fakeRumSessionId: String,
        @StringForgery fakeRumViewId: String,
        @StringForgery fakeRumActionId: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        stubSdkCore.stubFeature(Feature.RUM_FEATURE_NAME)
        stubSdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it["application_id"] = fakeRumApplicationId
            it["session_id"] = fakeRumSessionId
            it["view_id"] = fakeRumViewId
            it["action_id"] = fakeRumActionId
        }
        val testedTracer = AndroidTracer.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
        assertThat(event0.getString("spans[0].meta._dd.application.id")).isEqualTo(fakeRumApplicationId)
        assertThat(event0.getString("spans[0].meta._dd.session.id")).isEqualTo(fakeRumSessionId)
        assertThat(event0.getString("spans[0].meta._dd.view.id")).isEqualTo(fakeRumViewId)
    }

    @RepeatedTest(16)
    fun `M send sampled spans W setSampleRate() + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String,
        @DoubleForgery(0.0, 100.0) fakeSampleRate: Double
    ) {
        // Given
        val repeatCount = 256
        val expectedCount = (repeatCount * fakeSampleRate / 100f).toInt()
        val offsetMargin = repeatCount / 10 // allow 10% margin
        val testedTracer = AndroidTracer.Builder(stubSdkCore).setSampleRate(fakeSampleRate).build()

        // When
        repeat(repeatCount) {
            val span = testedTracer.buildSpan(fakeOperation).start()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        val keptSpans = eventsWritten.count {
            val asJson = JsonParser.parseString(it.eventData) as JsonObject
            assertThat(asJson.getDouble("spans[0].metrics._dd.agent_psr")).isCloseTo(
                fakeSampleRate / 100.0,
                offset(0.01)
            )
            asJson.getInt("spans[0].metrics._sampling_priority_v1") == 1
        }
        assertThat(keptSpans).isCloseTo(expectedCount, Offset.offset(offsetMargin))
    }

    @RepeatedTest(16)
    fun `M send span with defaults W buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String
    ) {
        // Given
        val testedTracer = AndroidTracer.Builder(stubSdkCore).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
    fun `M send trace with base user info W SDKCore#setUserInfo() + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String,
        @StringForgery fakeUserId: String,
        @StringForgery fakeUserName: String,
        @StringForgery fakeUserEmail: String
    ) {
        // Given
        stubSdkCore.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail)
        val testedTracer = AndroidTracer.Builder(stubSdkCore).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
        assertThat(event0.getString("spans[0].meta.usr.id")).isEqualTo(fakeUserId)
        assertThat(event0.getString("spans[0].meta.usr.name")).isEqualTo(fakeUserName)
        assertThat(event0.getString("spans[0].meta.usr.email")).isEqualTo(fakeUserEmail)
    }

    @RepeatedTest(16)
    fun `M send trace with custom user info W SDKCore#setUserInfo() + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String,
        @StringForgery fakeUserKey: String,
        @StringForgery fakeUserValue: String
    ) {
        // Given
        stubSdkCore.setUserInfo(extraInfo = mapOf(fakeUserKey to fakeUserValue))
        val testedTracer = AndroidTracer.Builder(stubSdkCore).build()

        // When
        var traceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            traceId = span.traceIdAsHexString()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
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
        assertThat(event0.getString("spans[0].meta.usr.$fakeUserKey")).isEqualTo(fakeUserValue)
    }

    @RepeatedTest(16)
    fun `M send span with parent W buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String
    ) {
        // Given
        val testedTracer = AndroidTracer.Builder(stubSdkCore).build()

        // When
        val span = testedTracer.buildSpan(fakeOperation).start()
        val traceId = span.traceIdAsHexString()
        val spanId = span.spanIdAsHexString()
        val childSpan = testedTracer.buildSpan(fakeOperation).asChildOf(span).start()
        val childTraceId = childSpan.traceIdAsHexString()
        val childSpanId = childSpan.spanIdAsHexString()
        Thread.sleep(OP_DURATION_MS)
        childSpan.finish()
        span.finish()

        // Then
        assertThat(childTraceId).isEqualTo(traceId)
        assertThat(childSpanId).isNotEqualTo(spanId)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        val event1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(traceId)
        assertThat(event0.getString("spans[0].span_id")).isEqualTo(spanId)
        assertThat(event1.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event1.getString("spans[0].trace_id")).isEqualTo(childTraceId)
        assertThat(event1.getString("spans[0].span_id")).isEqualTo(childSpanId)
    }

    companion object {
        const val OP_DURATION_MS = 10L
        val OP_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(OP_DURATION_MS)
    }
}
