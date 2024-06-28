/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.opentracing

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.tests.ktx.getInt
import com.datadog.android.tests.ktx.getLong
import com.datadog.android.tests.ktx.getString
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.integration.tests.elmyr.TraceIntegrationForgeConfigurator
import com.datadog.android.trace.model.SpanEvent
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.setStaticValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.util.GlobalTracer
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
class TraceConfigurationTest {

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
    fun `M create request to core site W RequestFactory#create()`(
        @Forgery fakeBatch: List<RawBatchEvent>,
        @StringForgery fakeMetadata: String
    ) {
        // Given
        val expectedSite = stubSdkCore.getDatadogContext().site
        val expectedClientToken = stubSdkCore.getDatadogContext().clientToken
        val expectedSource = stubSdkCore.getDatadogContext().source
        val expectedSdkVersion = stubSdkCore.getDatadogContext().sdkVersion

        // When
        val traceFeature = stubSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<StorageBackedFeature>()
        val requestFactory = traceFeature?.requestFactory
        val request = requestFactory?.create(stubSdkCore.getDatadogContext(), fakeBatch, fakeMetadata.toByteArray())

        // Then
        checkNotNull(request)
        assertThat(request.url).isEqualTo("${expectedSite.intakeEndpoint}/api/v2/spans")
        assertThat(request.headers).containsEntry("DD-API-KEY", expectedClientToken)
        assertThat(request.headers).containsEntry("DD-EVP-ORIGIN", expectedSource)
        assertThat(request.headers).containsEntry("DD-EVP-ORIGIN-VERSION", expectedSdkVersion)
        assertThat(request.contentType).isEqualTo("text/plain;charset=UTF-8")
    }

    @RepeatedTest(16)
    fun `M create request to custom endpoint W useCustomEndpoint() + RequestFactory#create`(
        @StringForgery fakeEndpoint: String,
        @Forgery fakeBatch: List<RawBatchEvent>,
        @StringForgery fakeMetadata: String
    ) {
        // Given
        val expectedClientToken = stubSdkCore.getDatadogContext().clientToken
        val expectedSource = stubSdkCore.getDatadogContext().source
        val expectedSdkVersion = stubSdkCore.getDatadogContext().sdkVersion
        val fakeTraceConfiguration = TraceConfiguration.Builder()
            .useCustomEndpoint(fakeEndpoint)
            .build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)

        // When
        val traceFeature = stubSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<StorageBackedFeature>()
        val requestFactory = traceFeature?.requestFactory
        val request = requestFactory?.create(stubSdkCore.getDatadogContext(), fakeBatch, fakeMetadata.toByteArray())

        // Then
        checkNotNull(request)
        assertThat(request.url).isEqualTo("$fakeEndpoint/api/v2/spans")
        assertThat(request.headers).containsEntry("DD-API-KEY", expectedClientToken)
        assertThat(request.headers).containsEntry("DD-EVP-ORIGIN", expectedSource)
        assertThat(request.headers).containsEntry("DD-EVP-ORIGIN-VERSION", expectedSdkVersion)
        assertThat(request.contentType).isEqualTo("text/plain;charset=UTF-8")
    }

    @RepeatedTest(16)
    fun `M send span without network info W setNetworkInfoEnabled(false) + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String
    ) {
        // Given
        val fakeTraceConfiguration = TraceConfiguration.Builder()
            .setNetworkInfoEnabled(false)
            .build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
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
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(leastSignificantTraceId)
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
        assertThat(event0.getString("spans[0].meta.network.client.connectivity")).isNull()
        assertThat(event0.getString("spans[0].meta.network.client.sim_carrier.name")).isNull()
        assertThat(event0.getString("spans[0].meta.network.client.sim_carrier.id")).isNull()
    }

    @RepeatedTest(16)
    fun `M send span with network info W setNetworkInfoEnabled(true) + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String
    ) {
        // Given
        val fakeTraceConfiguration = TraceConfiguration.Builder()
            .setNetworkInfoEnabled(true)
            .build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
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
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].trace_id")).isEqualTo(leastSignificantTraceId)
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
        assertThat(event0.getString("spans[0].meta.network.client.connectivity"))
            .isEqualTo(stubSdkCore.getDatadogContext().networkInfo.connectivity.name)
        assertThat(event0.getString("spans[0].meta.network.client.sim_carrier.name"))
            .isEqualTo(stubSdkCore.getDatadogContext().networkInfo.carrierName)
        assertThat(event0.getLong("spans[0].meta.network.client.sim_carrier.id"))
            .isEqualTo(stubSdkCore.getDatadogContext().networkInfo.carrierId)
    }

    @RepeatedTest(16)
    fun `M send mapped span W setEventMapper() + buildSpan() + start() + finish()`(
        @StringForgery fakeOperation: String,
        @StringForgery fakeMappedOperation: String,
        @StringForgery fakeMappedResource: String
    ) {
        // Given
        val stubMapper = object : SpanEventMapper {
            override fun map(event: SpanEvent): SpanEvent {
                event.name = fakeMappedOperation
                event.resource = fakeMappedResource
                return event
            }
        }
        val fakeTraceConfiguration = TraceConfiguration.Builder()
            .setEventMapper(stubMapper)
            .build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
        val testedTracer = AndroidTracer.Builder(stubSdkCore).build()

        // When
        val fullDuration = measureNanoTime {
            val span = testedTracer.buildSpan(fakeOperation).start()
            Thread.sleep(OP_DURATION_MS)
            span.finish()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("env")).isEqualTo(stubSdkCore.getDatadogContext().env)
        assertThat(event0.getString("spans[0].service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("spans[0].meta.version")).isEqualTo(stubSdkCore.getDatadogContext().version)
        assertThat(event0.getString("spans[0].meta._dd.source")).isEqualTo(stubSdkCore.getDatadogContext().source)
        assertThat(event0.getString("spans[0].meta.tracer.version"))
            .isEqualTo(stubSdkCore.getDatadogContext().sdkVersion)
        assertThat(event0.getInt("spans[0].error")).isEqualTo(0)
        assertThat(event0.getString("spans[0].name")).isEqualTo(fakeMappedOperation)
        assertThat(event0.getString("spans[0].resource")).isEqualTo(fakeMappedResource)
        assertThat(event0.getLong("spans[0].duration")).isBetween(OP_DURATION_NS, fullDuration)
    }

    companion object {
        const val OP_DURATION_MS = 10L
        val OP_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(OP_DURATION_MS)
    }
}
