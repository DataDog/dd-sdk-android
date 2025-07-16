/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.utils.toHexString
import com.datadog.android.okhttp.otel.addParentSpan
import com.datadog.android.okhttp.tests.assertj.SpansPayloadAssert
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.tests.ktx.getString
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.DatadogTracingToolkit
import com.datadog.android.trace.opentelemetry.OtelTracerProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeadBasedSamplingTest {

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore
        mockServer = MockWebServer()

        val fakeTraceConfiguration = TraceConfiguration.Builder().build()
        Trace.enable(fakeTraceConfiguration, stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        unregisterGlobalTracer()
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    private fun Request.Builder.parentSpan(span: DatadogSpan): Request.Builder {
        tag(DatadogSpan::class.java, span)
        return this
    }

    @Test
    fun `M use network sampling rate W call is made { no parent context }`(forge: Forge) {
        // Given
        if (forge.aBool()) {
            // outcome should be the same for cases where there is GlobalTracer and when local tracer is used
            registerGlobalTracer(0.0)
        }

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .build()
        ).execute()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("1")
        val leastSignificantTraceId = requestSent.getHeader(DATADOG_TRACE_ID_HEADER)
        checkNotNull(leastSignificantTraceId)
        val spanId = requestSent.getHeader(DATADOG_SPAN_ID_HEADER)
        checkNotNull(spanId)
        val datadogTags = requestSent.getHeader(DATADOG_TAGS_HEADER)
            ?.toTags()
        checkNotNull(datadogTags)
        assertThat(datadogTags).isNotEmpty
        assertThat(datadogTags).containsKey(MOST_SIGNIFICANT_TRACE_ID_KEY)
        val mostSignificantTraceId = datadogTags.getValue(MOST_SIGNIFICANT_TRACE_ID_KEY)

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)

        val spanPayload = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(spanPayload)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId.cleanDecimalFormat())
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName("okhttp.request")
                hasResource("http://${mockServer.hostName}:${mockServer.port}/")
                hasAgentPsr(1.0)
                hasSamplingPriority(1)
                hasGenericMetricValue("_top_level", 1)
                hasSpanKind("client")
                hasHttpMethod("GET")
                hasHttpUrl("http://${mockServer.hostName}:${mockServer.port}/")
                hasHttpStatusCode(200)
            }
    }

    @Test
    fun `M calculate PSR rate W call is made { no parent context }`(forge: Forge) {
        // Given
        if (forge.aBool()) {
            // outcome should be the same for cases where there is GlobalTracer and when local tracer is used
            registerGlobalTracer(0.0)
        }

        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(50f)
                    .build()
            )
            .build()

        // When

        repeat(10) {
            mockServer.enqueue(MockResponse())
            okHttpClient.newCall(
                Request.Builder()
                    .url(mockServer.url("/"))
                    .build()
            ).execute()
        }

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        eventsWritten.forEach {
            val spanPayload = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
            SpansPayloadAssert.assertThat(spanPayload)
                .hasEnv(stubSdkCore.getDatadogContext().env)
                .hasSpanAtIndexWith(0) {
                    hasAgentPsr(0.5)
                }
        }
    }

    @Test
    fun `M respect parent sampling decision W call is made { parent context = OpenTracing Span, parent not sampled }`(
        @StringForgery fakeSpanName: String
    ) {
        // Given
        registerGlobalTracer(0.0)

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        val parentSpan = GlobalDatadogTracer.get()
            .buildSpan(fakeSpanName)
            .start()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .parentSpan(parentSpan)
                .build()
        ).execute()
        parentSpan.finish()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("0")
        assertThat(requestSent.getHeader(DATADOG_TRACE_ID_HEADER)).isNotEmpty()
        assertThat(requestSent.getHeader(DATADOG_SPAN_ID_HEADER)).isNotEmpty()
        assertThat(requestSent.getHeader(DATADOG_TAGS_HEADER)).isNotEmpty()

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).isEmpty()
    }

    @Test
    fun `M respect parent sampling decision W call is made { parent context = OpenTelemetry Span, parent not sampled }`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeSpanName: String
    ) {
        // Given
        val otelTracer = OtelTracerProvider.Builder(stubSdkCore)
            .setTracingHeaderTypes(setOf(TracingHeaderType.DATADOG))
            .setSampleRate(0.0)
            .build()

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        val parentSpan = otelTracer.get(fakeInstrumentationName)
            .spanBuilder(fakeSpanName)
            .startSpan()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .addParentSpan(parentSpan)
                .build()
        ).execute()
        parentSpan.end()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("0")
        assertThat(requestSent.getHeader(DATADOG_TRACE_ID_HEADER)).isNotEmpty()
        assertThat(requestSent.getHeader(DATADOG_SPAN_ID_HEADER)).isNotEmpty()
        assertThat(requestSent.getHeader(DATADOG_TAGS_HEADER)).isNotEmpty()

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).isEmpty()
    }

    @Test
    fun `M respect parent sampling decision W call is made { parent context = OpenTracing Span, parent sampled }`(
        @StringForgery fakeSpanName: String
    ) {
        // Given
        registerGlobalTracer(100.0)

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(0f)
                    .build()
            )
            .build()

        val parentSpan = GlobalDatadogTracer.get()
            .buildSpan(fakeSpanName)
            .start()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .parentSpan(parentSpan)
                .build()
        ).execute()
        parentSpan.finish()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("2")
        val leastSignificantTraceId = requestSent.getHeader(DATADOG_TRACE_ID_HEADER)
        checkNotNull(leastSignificantTraceId)
        val spanId = requestSent.getHeader(DATADOG_SPAN_ID_HEADER)
        checkNotNull(spanId)
        val datadogTags = requestSent.getHeader(DATADOG_TAGS_HEADER)
            ?.toTags()
        checkNotNull(datadogTags)
        assertThat(datadogTags).isNotEmpty
        assertThat(datadogTags).containsKey(MOST_SIGNIFICANT_TRACE_ID_KEY)
        val mostSignificantTraceId = datadogTags.getValue(MOST_SIGNIFICANT_TRACE_ID_KEY)

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)

        val localSpanPayload = JsonParser.parseString(eventsWritten[0].eventData).asJsonObject
        SpansPayloadAssert.assertThat(localSpanPayload)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasParentId("0000000000000000")
                hasRulePsr(1.0)
                hasSamplingPriority(DatadogTracingConstants.PrioritySampling.USER_KEEP)
                hasGenericMetricValue("_top_level", 1)
            }

        val localSpanId = getLocalSpanId(localSpanPayload)

        val okHttpSpanPayload = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        SpansPayloadAssert.assertThat(okHttpSpanPayload)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId.cleanDecimalFormat())
                hasParentId(localSpanId.cleanHexFormat())
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName("okhttp.request")
                hasResource("http://${mockServer.hostName}:${mockServer.port}/")
                hasNoAgentPsr()
                hasSamplingPriority(2)
                hasNoGenericMetric("_top_level")
                hasSpanKind("client")
                hasHttpMethod("GET")
                hasHttpUrl("http://${mockServer.hostName}:${mockServer.port}/")
                hasHttpStatusCode(200)
            }
    }

    @Test
    fun `M respect parent sampling decision W call is made { parent context = OpenTelemetry Span, parent sampled }`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeSpanName: String
    ) {
        // Given
        val otelTracerProvider = OtelTracerProvider.Builder(stubSdkCore)
            .setTracingHeaderTypes(setOf(TracingHeaderType.DATADOG))
            .setSampleRate(100.0)
            .build()

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(0f)
                    .build()
            )
            .build()

        val parentSpan = otelTracerProvider.get(fakeInstrumentationName)
            .spanBuilder(fakeSpanName)
            .startSpan()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .addParentSpan(parentSpan)
                .build()
        ).execute()
        parentSpan.end()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("2")
        val leastSignificantTraceId = requestSent.getHeader(DATADOG_TRACE_ID_HEADER)
        checkNotNull(leastSignificantTraceId)
        val spanId = requestSent.getHeader(DATADOG_SPAN_ID_HEADER)
        checkNotNull(spanId)
        val datadogTags = requestSent.getHeader(DATADOG_TAGS_HEADER)
            ?.toTags()
        checkNotNull(datadogTags)
        assertThat(datadogTags).isNotEmpty
        assertThat(datadogTags).containsKey(MOST_SIGNIFICANT_TRACE_ID_KEY)
        val mostSignificantTraceId = datadogTags.getValue(MOST_SIGNIFICANT_TRACE_ID_KEY)

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)

        val localSpanPayload = JsonParser.parseString(eventsWritten[1].eventData).asJsonObject
        SpansPayloadAssert.assertThat(localSpanPayload)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasParentId("0000000000000000")
                // OpenTelemetry span will have _dd.rule_psr instead of _dd.agent_psr
                hasRulePsr(1.0)
                hasSamplingPriority(DatadogTracingConstants.PrioritySampling.USER_KEEP)
                hasGenericMetricValue("_top_level", 1)
            }

        val localSpanId = getLocalSpanId(localSpanPayload)

        val okHttpSpanPayload = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(okHttpSpanPayload)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId.cleanDecimalFormat())
                hasParentId(localSpanId.cleanHexFormat())
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName("okhttp.request")
                hasResource("http://${mockServer.hostName}:${mockServer.port}/")
                hasNoAgentPsr()
                hasSamplingPriority(DatadogTracingConstants.PrioritySampling.USER_KEEP)
                hasNoGenericMetric("_top_level")
                hasSpanKind("client")
                hasHttpMethod("GET")
                hasHttpUrl("http://${mockServer.hostName}:${mockServer.port}/")
                hasHttpStatusCode(200)
            }
    }

    @Test
    fun `M respect parent sampling decision W call is made {parent context = headers, parent not sampled}`(
        @StringForgery fakeSpanName: String
    ) {
        // Given
        registerGlobalTracer(0.0)

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        val parentSpan = GlobalDatadogTracer.get()
            .buildSpan(fakeSpanName)
            .start()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .apply {
                    GlobalDatadogTracer.get().propagate().inject(
                        context = parentSpan.context(),
                        carrier = this
                    ) { carrier, key, value -> carrier.addHeader(key, value) }
                }
                .build()
        ).execute()
        parentSpan.finish()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("0")
        assertThat(requestSent.getHeader(DATADOG_TRACE_ID_HEADER)).isNotEmpty()
        assertThat(requestSent.getHeader(DATADOG_SPAN_ID_HEADER)).isNotEmpty()
        assertThat(requestSent.getHeader(DATADOG_TAGS_HEADER)).isNotEmpty()

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).isEmpty()
    }

    @Test
    fun `M respect parent sampling decision W call is made { parent context=headers, parent sampled }`(
        @StringForgery fakeSpanName: String
    ) {
        // Given
        registerGlobalTracer(100.0)

        mockServer.enqueue(MockResponse())
        mockServer.start()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(
                    tracedHostsWithHeaderType = mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG))
                )
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .setTraceSampleRate(100f)
                    .build()
            )
            .build()

        val parentSpan = GlobalDatadogTracer.get()
            .buildSpan(fakeSpanName)
            .start()

        // When
        okHttpClient.newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .apply {
                    GlobalDatadogTracer.get()
                        .propagate()
                        .inject(parentSpan.context(), this) { _, key, value ->
                            addHeader(key, value)
                        }
                }
                .build()
        ).execute()
        parentSpan.finish()

        // Then
        val requestSent = mockServer.takeRequest()
        assertThat(requestSent.getHeader(DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("2")
        val leastSignificantTraceId = requestSent.getHeader(DATADOG_TRACE_ID_HEADER)
        checkNotNull(leastSignificantTraceId)
        val spanId = requestSent.getHeader(DATADOG_SPAN_ID_HEADER)
        checkNotNull(spanId)
        val datadogTags = requestSent.getHeader(DATADOG_TAGS_HEADER)
            ?.toTags()
        checkNotNull(datadogTags)
        assertThat(datadogTags).isNotEmpty
        assertThat(datadogTags).containsKey(MOST_SIGNIFICANT_TRACE_ID_KEY)
        val mostSignificantTraceId = datadogTags.getValue(MOST_SIGNIFICANT_TRACE_ID_KEY)

        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)

        val localSpanPayload = JsonParser.parseString(eventsWritten[1].eventData).asJsonObject
        SpansPayloadAssert.assertThat(localSpanPayload)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasParentId("0000000000000000")
                hasRulePsr(1.0)
                hasSamplingPriority(DatadogTracingConstants.PrioritySampling.USER_KEEP)
                hasGenericMetricValue("_top_level", 1)
            }

        val localSpanId = getLocalSpanId(localSpanPayload)

        val okHttpSpanPayload = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(okHttpSpanPayload)
            .hasEnv(stubSdkCore.getDatadogContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(
                    leastSignificantTraceId.toLong().toHexString().padStart(16, '0')
                )
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId.cleanDecimalFormat())
                hasParentId(localSpanId.cleanHexFormat())
                hasVersion(stubSdkCore.getDatadogContext().version)
                hasSource(stubSdkCore.getDatadogContext().source)
                hasTracerVersion(stubSdkCore.getDatadogContext().sdkVersion)
                hasError(0)
                hasName("okhttp.request")
                hasResource("http://${mockServer.hostName}:${mockServer.port}/")
                hasNoAgentPsr()
                // this one will have sampling priority unlike in case of propagation with tagged Span directly,
                // because there sampling priority is not yet set at the parent during child creation
                hasSamplingPriority(2)
                hasNoGenericMetric("_top_level")
                hasSpanKind("client")
                hasHttpMethod("GET")
                hasHttpUrl("http://${mockServer.hostName}:${mockServer.port}/")
                hasHttpStatusCode(200)
            }
    }

    private fun getLocalSpanId(localSpanPayload: JsonObject): String {
        return localSpanPayload.getAsJsonArray("spans")
            .first()
            .asJsonObject
            .getString("span_id")
            .orEmpty()
    }

    private fun registerGlobalTracer(sampleRate: Double) {
        GlobalDatadogTracer.registerIfAbsent(
            DatadogTracing.newTracerBuilder(stubSdkCore)
                .withTracingHeadersTypes(setOf(TracingHeaderType.DATADOG))
                // this is on purpose, we want to make sure that it is not taken into account
                .withSampleRate(sampleRate)
                .build()
        )
    }

    private fun unregisterGlobalTracer() {
        GlobalDatadogTracer.clear()
    }

    private fun String.toTags(): Map<String, String> = split(",")
        .associate { it.split("=").let { it[0] to it[1] } }

    private fun String.cleanHexFormat(): String = with(DatadogTracingToolkit.spanIdConverter) {
        toHexStringPadded(fromHex(this@cleanHexFormat))
    }

    private fun String.cleanDecimalFormat(): String = with(DatadogTracingToolkit.spanIdConverter) {
        toHexStringPadded(this@cleanDecimalFormat.toLong())
    }

    companion object {
        private const val DATADOG_TAGS_HEADER = "x-datadog-tags"
        private const val DATADOG_TRACE_ID_HEADER = "x-datadog-trace-id"
        private const val DATADOG_SPAN_ID_HEADER = "x-datadog-parent-id"
        private const val DATADOG_SAMPLING_PRIORITY_HEADER = "x-datadog-sampling-priority"
        private const val MOST_SIGNIFICANT_TRACE_ID_KEY = "_dd.p.tid"
    }
}
