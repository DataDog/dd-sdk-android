/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.okhttp.TraceContextInjection
import com.datadog.android.okhttp.internal.utils.forge.OkHttpConfigurator
import com.datadog.android.okhttp.utils.config.DatadogSingletonTestConfiguration
import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.legacy.trace.api.interceptor.MutableSpan
import com.datadog.opentracing.DDSpanContext
import com.datadog.opentracing.DDTracer
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigInteger

/**
 * Reproduces RUMS-3855: TracingInterceptor.handleW3CNotSampledHeaders() uses
 * span.context().toSpanId() which returns a DECIMAL string (Long.toString()).
 * When the decimal representation is >16 chars, the resulting traceparent header
 * exceeds 55 chars, violating the W3C Trace Context specification.
 *
 * The W3C spec requires:
 * - version: 2 hex chars ("00")
 * - traceId: 32 hex chars
 * - parentId: exactly 16 hex chars
 * - flags: 2 hex chars ("00" or "01")
 * - separators: 3 dashes
 * Total: exactly 55 chars for version 0.
 *
 * Long.MAX_VALUE = 9223372036854775807 (19 decimal digits).
 * Using this as a decimal spanId makes the traceparent 58 chars, which is invalid.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(OkHttpConfigurator::class)
internal class TracingInterceptorRUMS3855Test {

    lateinit var testedInterceptor: TracingInterceptor

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: DDTracer.DDSpanBuilder

    @Mock
    lateinit var mockSpanContext: DDSpanContext

    @Mock(extraInterfaces = [MutableSpan::class])
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequestListener: TracedRequestListener

    @Mock
    lateinit var mockResolver: DefaultFirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockTraceSampler: Sampler<Span>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    var fakeMediaType: MediaType? = null

    @StringForgery(type = StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String
    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    // A 32-char lowercase hex traceId
    @StringForgery(regex = "[a-f][0-9a-f]{31}")
    lateinit var fakeTraceIdHex: String

    lateinit var fakeTraceId: BigInteger

    lateinit var fakeLocalHosts: Map<String, Set<TracingHeaderType>>

    // The decimal representation of Long.MAX_VALUE is 19 digits.
    // padStart(16, '0') does NOT truncate strings already longer than 16 chars,
    // so the parentId field will be 19 chars, producing a 58-char traceparent
    // instead of the required 55.
    private val fakeDecimalSpanIdExceeding16Chars = Long.MAX_VALUE.toString()

    @BeforeEach
    fun setUp(forge: Forge) {
        fakeTraceId = BigInteger(fakeTraceIdHex, 16)

        whenever(mockTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.withOrigin(null)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.asChildOf(null as SpanContext?)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext
        // Return a decimal spanId >16 chars to trigger the bug
        whenever(mockSpanContext.toSpanId()) doReturn fakeDecimalSpanIdExceeding16Chars
        whenever(mockSpanContext.traceId).thenReturn(fakeTraceId)
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId.toString()
        // Span is NOT sampled, which triggers handleW3CNotSampledHeaders
        whenever(mockTraceSampler.sample(mockSpan)) doReturn false

        fakeMediaType = "application/json".toMediaTypeOrNull()

        val hostname = forge.aStringMatching(HOSTNAME_PATTERN)
        fakeLocalHosts = mapOf(hostname to setOf(TracingHeaderType.TRACECONTEXT))

        val protocol = forge.anElementFrom("http", "https")
        fakeUrl = "$protocol://$hostname/path?q=test"
        fakeRequest = Request.Builder()
            .url(fakeUrl)
            .get()
            .build()

        whenever(rumMonitor.mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mock()
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mockResolver
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl()))
            .thenReturn(setOf(TracingHeaderType.TRACECONTEXT))

        testedInterceptor = TracingInterceptor(
            sdkInstanceName = null,
            tracedHosts = fakeLocalHosts,
            tracedRequestListener = mockRequestListener,
            traceOrigin = null,
            traceSampler = mockTraceSampler,
            localTracerFactory = { _, _ -> mockLocalTracer },
            traceContextInjection = TraceContextInjection.All
        )

        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body(fakeResponseBody.toResponseBody(fakeMediaType))
            .build()

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        GlobalTracer.registerIfAbsent(mockTracer)
    }

    @AfterEach
    fun tearDown() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    /**
     * RUMS-3855 Bug: handleW3CNotSampledHeaders produces a traceparent with a decimal spanId.
     *
     * When span.context().toSpanId() returns a decimal string (Long.MAX_VALUE = 19 digits),
     * the resulting traceparent header exceeds 55 chars, violating W3C Trace Context spec.
     *
     * This test asserts the CORRECT behavior (traceparent must be exactly 55 chars) and
     * FAILS on the pre-fix code because the parentId is a 19-digit decimal string.
     */
    @Test
    fun `M produce valid 55-char traceparent W intercept() {not sampled, decimal spanId exceeds 16 chars}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .body(fakeResponseBody.toResponseBody(fakeMediaType))
            .build()
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // Precondition: the decimal spanId has more than 16 chars
        assertThat(fakeDecimalSpanIdExceeding16Chars.length)
            .describedAs("precondition: decimal spanId must exceed 16 chars to trigger bug")
            .isGreaterThan(TracingInterceptor.W3C_PARENT_ID_LENGTH)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            val traceparent = lastValue.header(TracingInterceptor.W3C_TRACEPARENT_KEY)
            assertThat(traceparent)
                .describedAs(
                    "traceparent header must be present when traceContextInjection=All and not sampled"
                )
                .isNotNull()

            // W3C spec: version-0 traceparent must be exactly 55 chars
            // "00-{32 hex traceId}-{16 hex parentId}-00"
            assertThat(traceparent!!.length)
                .describedAs(
                    "RUMS-3855: traceparent must be exactly 55 chars per W3C spec, " +
                        "but handleW3CNotSampledHeaders() uses toSpanId() (decimal) instead of hex encoding. " +
                        "Actual traceparent: '$traceparent' (${traceparent.length} chars)"
                )
                .isEqualTo(W3C_TRACEPARENT_TOTAL_LENGTH)
        }
    }

    /**
     * RUMS-3855 Bug (alternative assertion): the parentId segment of the traceparent header
     * must consist only of exactly 16 lowercase hex characters [0-9a-f].
     *
     * The pre-fix code passes the decimal string from toSpanId() directly into the parentId field.
     * A decimal string like "9223372036854775807" is 19 chars, which does not match [0-9a-f]{16}.
     */
    @Test
    fun `M produce hex-encoded parentId W intercept() {not sampled, toSpanId returns decimal}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .body(fakeResponseBody.toResponseBody(fakeMediaType))
            .build()
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            val traceparent = lastValue.header(TracingInterceptor.W3C_TRACEPARENT_KEY)
            assertThat(traceparent)
                .describedAs("traceparent header must be present")
                .isNotNull()

            // The full traceparent format is "00-{32hex}-{16hex}-00"
            // We assert that it matches the W3C pattern exactly.
            assertThat(traceparent)
                .describedAs(
                    "RUMS-3855: traceparent must match W3C pattern '00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]'. " +
                        "handleW3CNotSampledHeaders() uses toSpanId() which returns decimal, not hex. " +
                        "Actual value: '$traceparent'"
                )
                .matches(W3C_TRACEPARENT_PATTERN)
        }
    }

    companion object {
        // W3C Trace Context spec: "00-{32 hex}-{16 hex}-{2 hex flags}"
        private const val W3C_TRACEPARENT_PATTERN = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"
        private const val W3C_TRACEPARENT_TOTAL_LENGTH = 55

        private const val HOSTNAME_PATTERN =
            "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{1,4}[a-zA-Z0-9]{2,3})\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]{1,2}[A-Za-z0-9])"

        val datadogCore = DatadogSingletonTestConfiguration()
        val rumMonitor = GlobalRumMonitorTestConfiguration(datadogCore)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(datadogCore, rumMonitor)
    }
}
