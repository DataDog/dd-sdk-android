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
import fr.xgouchet.elmyr.annotation.StringForgery
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(OkHttpConfigurator::class)
internal class TracingInterceptorTraceparentTest {

    lateinit var testedInterceptor: TracingInterceptor

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

    @StringForgery(regex = "[a-f][0-9]{32}")
    lateinit var fakeTraceIdAsString: String

    lateinit var fakeTraceId: BigInteger
    lateinit var fakeUrl: String
    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response
    lateinit var fakeLocalHosts: Map<String, Set<TracingHeaderType>>
    var fakeMediaType: MediaType? = null
    lateinit var fakeResponseBody: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTraceId = BigInteger(fakeTraceIdAsString, 16)
        fakeResponseBody = forge.anAlphabeticalString()
        val mediaType = "application/json"
        fakeMediaType = mediaType.toMediaTypeOrNull()

        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.withOrigin(null)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.asChildOf(null as SpanContext?)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext

        // Simulate the real DDSpanContext.toSpanId() behavior:
        // Long.MAX_VALUE = 9223372036854775807 in decimal (19 chars) vs 7fffffffffffffff in hex (16 chars).
        // The real DDSpanContext.toSpanId() returns spanId.toString() - a decimal BigInteger string.
        // This is the root of the bug: decimal strings >16 chars are not truncated by padStart(16).
        val largeDecimalSpanId = Long.MAX_VALUE.toString() // "9223372036854775807" - 19 decimal chars
        whenever(mockSpanContext.toSpanId()) doReturn largeDecimalSpanId
        whenever(mockSpanContext.traceId).thenReturn(fakeTraceId)
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId.toString()

        // Non-sampled: the sampler returns false, calls handleW3CNotSampledHeaders
        whenever(mockTraceSampler.sample(mockSpan)) doReturn false

        val host = forge.aStringMatching(HOSTNAME_PATTERN)
        fakeLocalHosts = mapOf(host to setOf(TracingHeaderType.TRACECONTEXT))
        fakeUrl = "https://$host/test"
        fakeRequest = Request.Builder().url(fakeUrl).get().build()
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .header(TracingInterceptor.HEADER_CT, "application/json")
            .body(fakeResponseBody.toResponseBody(fakeMediaType))
            .build()

        whenever(rumMonitor.mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mock()
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mockResolver
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)

        testedInterceptor = TracingInterceptor(
            sdkInstanceName = null,
            tracedHosts = fakeLocalHosts,
            tracedRequestListener = mockRequestListener,
            traceOrigin = null,
            traceSampler = mockTraceSampler,
            localTracerFactory = { _, _ -> mockLocalTracer },
            traceContextInjection = TraceContextInjection.All
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    /**
     * Reproduces RUMS-3855.
     *
     * When a request is not sampled (80% of requests with default 20% sampling rate),
     * TracingInterceptor.handleW3CNotSampledHeaders() uses span.context().toSpanId() to populate
     * the parent-id field of the W3C traceparent header.
     *
     * DDSpanContext.toSpanId() returns spanId.toString() - a decimal BigInteger string.
     * For a 63-bit span ID like Long.MAX_VALUE:
     *   - Decimal: "9223372036854775807" (19 characters)
     *   - Hex:     "7fffffffffffffff"    (16 characters, as required by W3C spec)
     *
     * The call to padStart(length = 16, padChar = '0') is a no-op when the decimal string is
     * already 17-19 chars long, so the malformed decimal string is written verbatim into the
     * traceparent header. Backends reject the header with "Invalid traceparent".
     *
     * This test FAILS on the pre-fix commit because toSpanId() returns a 19-digit decimal string,
     * making the parent-id field fail the [a-f0-9]{16} regex check.
     */
    @Test
    fun `M produce valid W3C traceparent parent-id W intercept() {not sampled, decimal spanId from DDSpanContext}`() {
        // Given
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then: capture the request that was passed to chain.proceed()
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            val traceparent = lastValue.header(TracingInterceptor.W3C_TRACEPARENT_KEY)

            assertThat(traceparent)
                .withFailMessage("Expected traceparent header to be present in the request")
                .isNotNull()

            val parts = traceparent!!.split("-")

            // W3C traceparent format: 00-{32-hex-traceId}-{16-hex-parentId}-{2-hex-flags}
            assertThat(parts)
                .withFailMessage(
                    "Expected traceparent to have exactly 4 fields separated by '-', but was: $traceparent"
                )
                .hasSize(4)

            val parentId = parts[2]

            // This assertion FAILS on the pre-fix commit:
            // toSpanId() returns "9223372036854775807" (19 decimal chars) which does not match
            // [a-f0-9]{16}. After the fix, the span ID must be formatted as hex before padding.
            assertThat(parentId)
                .withFailMessage(
                    "Expected traceparent parent-id to be exactly 16 lowercase hex characters " +
                        "as required by the W3C traceparent spec, but was: '$parentId' " +
                        "(length=${parentId.length}). " +
                        "This indicates DDSpanContext.toSpanId() returned a decimal string instead " +
                        "of hex - RUMS-3855."
                )
                .matches("[a-f0-9]{16}")

            // Also assert total header length: "00-" + 32 + "-" + 16 + "-" + "00" = 55 chars
            assertThat(traceparent.length)
                .withFailMessage(
                    "Expected traceparent to be exactly 55 characters, but was ${traceparent.length}: $traceparent"
                )
                .isEqualTo(55)
        }
    }

    companion object {
        const val HOSTNAME_PATTERN =
            "(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{1,4}[a-zA-Z0-9]{2,3})\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]{1,2}[A-Za-z0-9])"

        val datadogCore = DatadogSingletonTestConfiguration()
        val rumMonitor = GlobalRumMonitorTestConfiguration(datadogCore)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadogCore, rumMonitor)
        }
    }
}
