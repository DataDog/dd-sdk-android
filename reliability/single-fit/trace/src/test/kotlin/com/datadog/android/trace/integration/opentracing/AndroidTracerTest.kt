/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.opentracing

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.tests.ktx.getInt
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.integration.tests.assertj.SpansPayloadAssert
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasSpanId(spanId)
                hasService(fakeService)
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue(fakeTagKey, fakeTagValue)
            }
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue(fakeTagKey, fakeTagValue)
            }
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasApplicationId(null)
                hasSessionId(null)
                hasViewId(null)
            }
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasApplicationId(fakeRumApplicationId)
                hasSessionId(fakeRumSessionId)
                hasViewId(fakeRumViewId)
            }
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
            val payload = JsonParser.parseString(it.eventData) as JsonObject
            SpansPayloadAssert.assertThat(payload).hasSpanAtIndexWith(0) {
                hasAgentPsrCloseTo(fakeSampleRate / 100.0, offset(0.01))
            }
            payload.getInt("spans[0].metrics._sampling_priority_v1") == 1
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
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
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasUserId(fakeUserId)
                hasUserName(fakeUserName)
                hasUserEmail(fakeUserEmail)
            }
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
        var leastSignificantTraceId: String
        var spanId: String
        var mostSignificantTraceId: String
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            leastSignificantTraceId = span.leastSignificant64BitsTraceId()
            mostSignificantTraceId = span.mostSignificant64BitsTraceId()
            spanId = span.spanIdAsHexString()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
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
                hasName(fakeOperation)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasGenericMetaValue("usr.$fakeUserKey", fakeUserValue)
            }
    }

    @RepeatedTest(16)
    fun `M send span with parent W buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String
    ) {
        // Given
        val testedTracer = AndroidTracer.Builder(stubSdkCore).build()

        // When
        val span = testedTracer.buildSpan(fakeOperation).start()
        val leastSignificantTraceId = span.leastSignificant64BitsTraceId()
        val mostSignificantTraceId = span.mostSignificant64BitsTraceId()
        val spanId = span.spanIdAsHexString()
        val childSpan = testedTracer.buildSpan(fakeOperation).asChildOf(span).start()
        val childLeastSignificantTraceId = childSpan.leastSignificant64BitsTraceId()
        val childMostSignificantTraceId = childSpan.mostSignificant64BitsTraceId()
        val childSpanId = childSpan.spanIdAsHexString()
        Thread.sleep(OP_DURATION_MS)
        childSpan.finish()
        span.finish()

        // Then
        assertThat(childLeastSignificantTraceId).isEqualTo(leastSignificantTraceId)
        assertThat(childMostSignificantTraceId).isEqualTo(mostSignificantTraceId)
        assertThat(childSpanId).isNotEqualTo(spanId)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        val payload1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(spanId)
            }
        SpansPayloadAssert.assertThat(payload1)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(childLeastSignificantTraceId)
                hasMostSignificant64BitsTraceId(childMostSignificantTraceId)
                hasValidMostSignificant64BitsTraceId()
                hasValidLeastSignificant64BitsTraceId()
                hasSpanId(childSpanId)
            }
    }

    companion object {
        const val OP_DURATION_MS = 10L
        val OP_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(OP_DURATION_MS)
    }
}
