/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.okhttp.TraceContextInjection
import com.datadog.android.okhttp.utils.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.okhttp.utils.config.DatadogSingletonTestConfiguration
import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.okhttp.utils.verifyLog
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.trace.TracingHeaderType
import com.datadog.legacy.trace.api.interceptor.MutableSpan
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.SpanBuilder
import com.datadog.trace.bootstrap.instrumentation.api.Tags
import com.datadog.trace.core.propagation.ExtractedContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal open class TracingInterceptorNotSendingSpanTest {

    lateinit var testedInterceptor: TracingInterceptor

    // region Mocks

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockPropagation: AgentPropagation

    @Mock
    lateinit var mockSpanBuilder: SpanBuilder

    @Mock
    lateinit var mockSpanContext: SpanContext

    @Mock(extraInterfaces = [MutableSpan::class])
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequestListener: TracedRequestListener

    @Mock
    lateinit var mockResolver: DefaultFirstPartyHostHeaderTypeResolver

    lateinit var mockTraceSampler: Sampler<Span>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // endregion

    // region Fakes

    @StringForgery(regex = HOSTNAME_PATTERN)
    lateinit var fakeHostName: String

    @StringForgery(regex = IPV4_PATTERN)
    lateinit var fakeHostIp: String

    lateinit var fakeMethod: RumResourceMethod
    var fakeBody: String? = null
    var fakeMediaType: MediaType? = null

    @StringForgery(type = StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @LongForgery
    var fakeSpanId: Long = 0L

    @StringForgery(regex = "[a-f][0-9]{32}")
    lateinit var fakeTraceIdAsString: String

    private lateinit var fakeTraceId: DDTraceId

    private var fakeOrigin: String? = null

    lateinit var fakeLocalHosts: Map<String, Set<TracingHeaderType>>

    @BoolForgery
    var fakeRedacted404Resources: Boolean = true

    // endregion

    @BeforeEach
    open fun `set up`(forge: Forge) {
        fakeTraceId = forge.aDDTraceId(fakeTraceIdAsString)
        fakeOrigin = forge.aNullable { anAlphabeticalString() }
        mockSpanBuilder = forge.newSpanBuilderMock()
        mockSpanContext = forge.newSpanContextMock(fakeTraceId, fakeSpanId)
        mockSpan = forge.newSpanMock(mockSpanContext)
        mockPropagation = newAgentPropagationMock()
        mockTracer = forge.newTracerMock(mockSpanBuilder, mockPropagation)
        mockLocalTracer = forge.newTracerMock(mockSpanBuilder, mockPropagation)
        mockTraceSampler = forge.newTraceSamplerMock(mockSpan)

        fakeMediaType = if (forge.aBool()) {
            val mediaType = forge.anElementFrom("application", "image", "text", "model") +
                "/" + forge.anAlphabeticalString()
            mediaType.toMediaTypeOrNull()
        } else {
            null
        }
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.DATADOG
            )
        }
        fakeUrl = forgeUrl(forge)
        fakeRequest = forgeRequest(forge)
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mock()
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mockResolver
        doAnswer { false }.whenever(mockResolver).isFirstPartyUrl(any<HttpUrl>())
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(fakeUrl.toHttpUrl())

        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }
    }

    open fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>>,
        factory: (SdkCore, Set<TracingHeaderType>) -> Tracer
    ): TracingInterceptor {
        return object :
            TracingInterceptor(
                sdkInstanceName = null,
                tracedHosts = tracedHosts,
                tracedRequestListener = mockRequestListener,
                traceOrigin = fakeOrigin,
                traceSampler = mockTraceSampler,
                localTracerFactory = factory,
                globalTracerProvider = { null },
                redacted404ResourceName = fakeRedacted404Resources,
                traceContextInjection = TraceContextInjection.ALL
            ) {
            override fun canSendSpan(): Boolean {
                return false
            }
        }
    }

    open fun getExpectedOrigin(): String? {
        return fakeOrigin
    }

    @Test
    fun `M inject tracing header W intercept() {global known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                forge.anElementFrom(
                    setOf(
                        TracingHeaderType.DATADOG,
                        TracingHeaderType.B3,
                        TracingHeaderType.B3MULTI,
                        TracingHeaderType.TRACECONTEXT
                    )
                )
            )
        )
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
    }

    @Test
    fun `M inject non-tracing header W intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.DATADOG
            )
        )
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TAGS_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_ORIGIN_HEADER)).isNull()
        }
    }

    @Test
    fun `M inject non-tracing b3mult header W intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.B3MULTI
            )
        )
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.B3M_SPAN_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_TRACE_ID_KEY)).isNull()
        }
    }

    @Test
    fun `M inject non-tracing b3 header W intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.B3
            )
        )
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.B3_HEADER_KEY))
                .isEqualTo("0")
        }
    }

    @Test
    fun `M inject non-tracing tracecontext header W intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.TRACECONTEXT
            )
        )
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.headers)
                .hasTraceParentHeader(
                    fakeTraceIdAsString,
                    mockSpan.context().spanId.toString(),
                    isSampled = false
                )
                .hasTraceStateHeaderWithOnlyDatadogVendorValues(
                    mockSpan.context().spanId.toString(),
                    isSampled = false,
                    getExpectedOrigin()
                )
        }
    }

    @Test
    fun `M inject all non-tracing header W intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.DATADOG,
                TracingHeaderType.B3,
                TracingHeaderType.B3MULTI,
                TracingHeaderType.TRACECONTEXT
            )
        )
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_ORIGIN_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TAGS_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.B3M_SPAN_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_TRACE_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3_HEADER_KEY))
                .isEqualTo("0")
            assertThat(lastValue.headers)
                .hasTraceParentHeader(
                    fakeTraceIdAsString,
                    mockSpan.context().spanId.toString(),
                    isSampled = false
                )
                .hasTraceStateHeaderWithOnlyDatadogVendorValues(
                    mockSpan.context().spanId.toString(),
                    isSampled = false,
                    getExpectedOrigin()
                )
        }
    }

    @Test
    fun `M inject tracing header W intercept() {local known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        fakeUrl = forgeUrl(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
    }

    @Test
    fun `M inject non-tracing header W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeUrl = forgeUrl(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TAGS_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_ORIGIN_HEADER)).isNull()
        }
    }

    @Test
    fun `M inject non-tracing b3multi header W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.B3MULTI
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }
        fakeUrl = forgeUrl(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.B3M_SPAN_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_TRACE_ID_KEY)).isNull()
        }
    }

    @Test
    fun `M inject non-tracing b3 header W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.B3
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }
        fakeUrl = forgeUrl(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.B3_HEADER_KEY))
                .isEqualTo("0")
        }
    }

    @Test
    fun `M inject non-tracing tracecontext header W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.TRACECONTEXT
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }
        fakeUrl = forgeUrl(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.headers)
                .hasTraceParentHeader(
                    fakeTraceIdAsString,
                    mockSpan.context().spanId.toString(),
                    isSampled = false
                )
                .hasTraceStateHeaderWithOnlyDatadogVendorValues(
                    mockSpan.context().spanId.toString(),
                    isSampled = false,
                    getExpectedOrigin()
                )
        }
    }

    @Test
    fun `M inject tracing header W intercept() for request with parent span`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        val parentSpan: Span = mock()
        val parentSpanContext: SpanContext = mock()
        whenever(parentSpan.context()) doReturn parentSpanContext
        whenever(mockSpanBuilder.asChildOf(parentSpanContext)) doReturn mockSpanBuilder
        fakeRequest = forgeRequest(forge) { it.tag(Span::class.java, parentSpan) }
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(fakeUrl.toHttpUrl())
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
        verify(mockSpanBuilder).asChildOf(parentSpanContext)
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
    }

    @Test
    fun `M update header with parent context W intercept() for request with tracing headers`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        val parentSpanContext: ExtractedContext = mock()
        whenever(mockPropagation.extract(any<Request>(), any())) doReturn parentSpanContext
        whenever(mockSpanBuilder.asChildOf(any<SpanContext>())) doReturn mockSpanBuilder
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        fakeRequest = forgeRequest(forge)
        doAnswer { true }.whenever(mockResolver).isFirstPartyUrl(fakeUrl.toHttpUrl())
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
        verify(mockSpanBuilder).asChildOf(parentSpanContext)
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
    }

    @Test
    fun `M respect sampling decision W intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER,
                forge.anElementFrom(
                    PrioritySampling.SAMPLER_KEEP.toString(),
                    PrioritySampling.USER_KEEP.toString()
                )
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
    }

    @Test
    fun `M respect b3multi sampling decision W intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY,
                PrioritySampling.SAMPLER_KEEP.toString()
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
    }

    @Test
    fun `M respect b3 sampling decision W intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3_HEADER_KEY,
                forge.aStringMatching("[a-f0-9]{32}\\-[a-f0-9]{16}\\-1")
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
    }

    @Test
    fun `M respect tracecontext sampling decision W intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.W3C_TRACEPARENT_KEY,
                forge.aStringMatching("00-[a-f0-9]{32}\\-[a-f0-9]{16}\\-01")
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(key)).isEqualTo(value)
        }
    }

    @Test
    fun `M respect sampling decision W intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.DATADOG
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER,
                forge.anElementFrom(
                    PrioritySampling.SAMPLER_DROP.toString(),
                    PrioritySampling.USER_DROP.toString()
                )
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(true)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TAGS_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_ORIGIN_HEADER)).isNull()
        }
    }

    @Test
    fun `M respect b3multi sampling decision W intercept1`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        val localSpanBuilder = forge.newSpanBuilderMock()
        whenever(mockLocalTracer.buildSpan(any<CharSequence>())).thenReturn(localSpanBuilder)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(setOf(TracingHeaderType.B3MULTI))

        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY,
                PrioritySampling.SAMPLER_DROP.toString()
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(true)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY)).isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.B3M_SPAN_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_TRACE_ID_KEY)).isNull()
        }
    }

    @Test
    fun `M respect b3 sampling decision W intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.B3
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3_HEADER_KEY,
                forge.anElementFrom(
                    PrioritySampling.SAMPLER_DROP.toString(),
                    forge.aStringMatching("[a-f0-9]{32}\\-[a-f0-9]{16}\\-0")
                )
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(true)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.B3_HEADER_KEY))
                .isEqualTo("0")
        }
    }

    @Test
    fun `M respect tracecontext sampling decision W intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.TRACECONTEXT
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.W3C_TRACEPARENT_KEY,
                forge.aStringMatching("00-[a-f0-9]{32}\\-[a-f0-9]{16}\\-00")
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(true)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.headers)
                .hasTraceParentHeader(
                    fakeTraceIdAsString,
                    mockSpan.context().spanId.toString(),
                    isSampled = false
                )
                .hasTraceStateHeaderWithOnlyDatadogVendorValues(
                    mockSpan.context().spanId.toString(),
                    isSampled = false,
                    getExpectedOrigin()
                )
        }
    }

    @Test
    fun `M create a span with info W intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M create a span with info W intercept() for failing request {4xx}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setResourceName(fakeUrl)
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        verify(mockSpan).setError(true)
        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M create a span with info W intercept() for failing request {5xx}`(
        @IntForgery(min = 500, max = 600) statusCode: Int
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setResourceName(fakeUrl)
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan, never()).setError(true)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M create a span with info W intercept() for 404 request`() {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, 404)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", 404)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        verify(mockSpan).setError(true)
        if (fakeRedacted404Resources) {
            verify(mockSpan).setResourceName(TracingInterceptor.RESOURCE_NAME_404)
        } else {
            verify(mockSpan, never()).setResourceName(TracingInterceptor.RESOURCE_NAME_404)
        }
        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M create a span with info W intercept() for throwing request`(
        @Forgery throwable: Throwable
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
    }

    @Test
    fun `M warn the user W intercept() no tracer registered and TracingFeature not initialized`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null
        stubChain(mockChain, statusCode)

        testedInterceptor.intercept(mockChain)

        verifyNoInteractions(mockLocalTracer)
        verifyNoInteractions(mockTracer)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            TracingInterceptor.WARNING_TRACING_DISABLED,
            null,
            true
        )
    }

    @Test
    fun `M create a span with automatic tracer W intercept() if no tracer registered`(
        forge: Forge,
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val localSpan: Span = forge.newSpanMock(mockSpanContext)
        val localSpanBuilder: SpanBuilder = forge.newSpanBuilderMock(localSpan)

        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(localSpan)).thenReturn(true)
        whenever(mockSpanContext.spanId) doReturn fakeSpanId
        whenever(mockSpanContext.traceId) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        val response = testedInterceptor.intercept(mockChain)

        verify(localSpanBuilder).withOrigin(getExpectedOrigin())
        verify(localSpan).setTag("http.url", fakeUrl)
        verify(localSpan).setTag("http.method", fakeMethod.name)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(localSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        verify(localSpan, never()).finish()
        verify(localSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            TracingInterceptor.WARNING_DEFAULT_TRACER
        )
    }

    @Test
    fun `M drop automatic tracer W intercept() and global tracer registered`(
        forge: Forge,
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val localSpan: Span = forge.newSpanMock()
        val localSpanBuilder: SpanBuilder = forge.newSpanBuilderMock(localSpan)

        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(localSpan)).thenReturn(true)
        whenever(mockSpanContext.spanId) doReturn fakeSpanId
        whenever(mockSpanContext.traceId) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        val response1 = testedInterceptor.intercept(mockChain)
        val expectedResponse1 = fakeResponse
        stubChain(mockChain, statusCode)
        val response2 = testedInterceptor.intercept(mockChain)
        val expectedResponse2 = fakeResponse

        verify(localSpanBuilder).withOrigin(getExpectedOrigin())
        verify(localSpan).setTag("http.url", fakeUrl)
        verify(localSpan).setTag("http.method", fakeMethod.name)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(localSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)

        verify(localSpan, never()).finish()
        verify(localSpan as MutableSpan).drop()
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)

        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response1).isSameAs(expectedResponse1)
        assertThat(response2).isSameAs(expectedResponse2)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            TracingInterceptor.WARNING_DEFAULT_TRACER
        )
    }

    @Test
    fun `M call the listener W intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery tagKey: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)

        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M call the listener W intercept() for failing request`(
        @IntForgery(min = 400, max = 600) statusCode: Int,
        @StringForgery tagKey: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)

        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M not call the listener W intercept() for completed request { not sampled }`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verifyNoInteractions(mockRequestListener)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M call the listener W intercept() for throwing request`(
        @Forgery throwable: Throwable,
        @StringForgery tagKey: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod.name)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)

        verify(mockSpan, never()).finish()
        verify(mockSpan as MutableSpan).drop()
    }

    @Test
    fun `M not call the listener W intercept() for throwing request { not sampled }`(
        @Forgery throwable: Throwable
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verifyNoInteractions(mockRequestListener)
    }

    @Test
    fun `M do nothing W intercept() for request with unknown host`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verifyNoInteractions(mockTracer, mockLocalTracer)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M warn once W intercept() with no known host`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockResolver.isEmpty()) doReturn true
        whenever(mockResolver.isFirstPartyUrl(any<String>())) doReturn false
        whenever(mockResolver.isFirstPartyUrl(any<HttpUrl>())) doReturn false
        testedInterceptor = instantiateTestedInterceptor(emptyMap()) { _, _ -> mockLocalTracer }
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)
        testedInterceptor.intercept(mockChain)

        // Then
        verifyNoInteractions(mockTracer, mockLocalTracer)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            TracingInterceptor.WARNING_TRACING_NO_HOSTS,
            null,
            true
        )
    }

    @Test
    fun `M create only one local tracer W intercept() called from multiple threads`(
        forge: Forge,
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        var called = 0
        val tracedHosts =
            listOf(fakeHostIp, fakeHostName).associateWith { setOf(TracingHeaderType.DATADOG) }
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        testedInterceptor = instantiateTestedInterceptor(tracedHosts) { _, _ ->
            called++
            mockLocalTracer
        }
        stubChain(mockChain, statusCode)

        // need this setup, otherwise #intercept actually throws NPE, which pollutes the log
        val localSpan: Span = forge.newSpanMock(mockSpanContext)
        val localSpanBuilder: SpanBuilder = forge.newSpanBuilderMock(localSpan)
        whenever(mockSpanContext.spanId) doReturn fakeSpanId
        whenever(mockSpanContext.traceId) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        // When
        val countDownLatch = CountDownLatch(2)
        Thread {
            testedInterceptor.intercept(mockChain)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedInterceptor.intercept(mockChain)
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(1, TimeUnit.SECONDS)
        verify(mockLocalTracer, times(2)).buildSpan(TracingInterceptor.SPAN_NAME)
        assertThat(called).isEqualTo(1)
    }

    // region Internal

    internal fun stubChain(
        chain: Interceptor.Chain,
        statusCode: Int,
        responseBuilder: Response.Builder.() -> Unit = {}
    ) {
        return stubChain(chain) { forgeResponse(statusCode, responseBuilder) }
    }

    internal fun stubChain(
        chain: Interceptor.Chain,
        responseBuilder: () -> Response
    ) {
        fakeResponse = responseBuilder()

        whenever(chain.request()) doReturn fakeRequest
        whenever(chain.proceed(any())) doReturn fakeResponse
    }

    private fun forgeUrl(forge: Forge, knownHost: String? = null): String {
        val protocol = forge.anElementFrom("http", "https")
        val host = knownHost ?: forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN)
        val path = forge.anAlphaNumericalString()
        // RUMM-2900 host is by definition case insensitive,
        // and OkHttp lowercases it when building the request
        return "$protocol://${host.lowercase(Locale.US)}/$path"
    }

    protected fun forgeRequest(
        forge: Forge,
        configure: (Request.Builder) -> Unit = {}
    ): Request {
        val builder = Request.Builder().url(fakeUrl)
        if (forge.aBool()) {
            fakeMethod = forge.anElementFrom(
                RumResourceMethod.POST,
                RumResourceMethod.PATCH,
                RumResourceMethod.PUT
            )
            fakeBody = forge.anAlphabeticalString()
            builder.method(fakeMethod.name, fakeBody!!.toByteArray().toRequestBody(null))
        } else {
            fakeMethod = forge.anElementFrom(
                RumResourceMethod.GET,
                RumResourceMethod.HEAD,
                RumResourceMethod.DELETE,
                RumResourceMethod.CONNECT,
                RumResourceMethod.TRACE,
                RumResourceMethod.OPTIONS
            )
            fakeBody = null
            builder.method(fakeMethod.name, null)
        }

        configure(builder)

        return builder.build()
    }

    private fun forgeResponse(statusCode: Int, additionalConfig: Response.Builder.() -> Unit = {}): Response {
        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .body(fakeResponseBody.toResponseBody(fakeMediaType))
        if (fakeMediaType != null) {
            builder.header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
        }
        builder.additionalConfig()
        return builder.build()
    }

    // endregion

    companion object {
        const val HOSTNAME_PATTERN = "([a-z][a-z0-9_~-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
        const val IPV4_PATTERN =
            "(([0-9]|[1-9][0-9]|1[0-9]){2}\\.|(2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"

        val datadogCore = DatadogSingletonTestConfiguration()
        val rumMonitor = GlobalRumMonitorTestConfiguration(datadogCore)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadogCore, rumMonitor)
        }
    }
}
