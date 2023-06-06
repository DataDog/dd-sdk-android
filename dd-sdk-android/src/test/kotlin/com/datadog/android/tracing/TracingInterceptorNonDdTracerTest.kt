/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.opentracing.DDTracer
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import com.datadog.trace.api.interceptor.MutableSpan
import com.datadog.trace.api.sampling.PrioritySampling
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.TextMapExtract
import io.opentracing.propagation.TextMapInject
import io.opentracing.util.GlobalTracer
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This test is a duplicate of [TracingInterceptorTest] but assuming the Tracer is not our own
 * implementation and therefore doesn't implement DD specific methods.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal open class TracingInterceptorNonDdTracerTest {

    lateinit var testedInterceptor: TracingInterceptor

    // region Mocks

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder

    @Mock
    lateinit var mockSpanContext: SpanContext

    @Mock
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequestListener: TracedRequestListener

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockTraceSampler: Sampler

    // endregion

    // region Fakes

    lateinit var fakeMethod: String
    var fakeBody: String? = null
    var fakeMediaType: MediaType? = null

    @StringForgery(type = StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String

    lateinit var fakeBaseUrl: String

    @Forgery
    lateinit var fakeConfig: Configuration.Feature.Tracing

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    @StringForgery
    lateinit var fakeOrigin: String

    lateinit var fakeLocalHosts: Map<String, Set<TracingHeaderType>>

    // endregion

    @BeforeEach
    fun `set up`(forge: Forge) {
        Datadog.setVerbosity(Log.VERBOSE)

        whenever(mockTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.asChildOf(null as SpanContext?)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        whenever(mockTraceSampler.sample()) doReturn true

        val mediaType = forge.anElementFrom("application", "image", "text", "model") +
            "/" + forge.anAlphabeticalString()
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.DATADOG
            )
        }
        fakeMediaType = MediaType.parse(mediaType)
        fakeUrl = forgeUrlWithQueryParams(forge)
        fakeRequest = forgeRequest(forge)
        val mockCore = mock<DatadogCore>()
        whenever(mockCore.tracingFeature) doReturn mock()
        Datadog.globalSdkCore = mockCore
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) {
            mockLocalTracer
        }

        GlobalTracer.registerIfAbsent(mockTracer)
    }

    open fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>> = emptyMap(),
        factory: (Set<TracingHeaderType>) -> Tracer
    ): TracingInterceptor {
        return TracingInterceptor(
            tracedHosts,
            mockRequestListener,
            mockResolver,
            fakeOrigin,
            mockTraceSampler,
            factory
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.globalSdkCore = NoOpSdkCore()
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `M instantiate with default values W init()`() {
        // When
        val interceptor = TracingInterceptor()

        // Then
        assertThat(interceptor.tracedHosts).isEmpty()
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(RateBasedSampler::class.java)
        val traceSampler = interceptor.traceSampler as RateBasedSampler
        assertThat(traceSampler.sampleRate).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLING_RATE / 100
        )
    }

    @Test
    fun `M instantiate with default values W init()`(
        @StringForgery(regex = "[a-z]+\\.[a-z]{3}") hosts: List<String>
    ) {
        // When
        val interceptor = TracingInterceptor(hosts)

        // Then
        assertThat(interceptor.tracedHosts.keys).containsAll(hosts)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(RateBasedSampler::class.java)
        val traceSampler = interceptor.traceSampler as RateBasedSampler
        assertThat(traceSampler.sampleRate).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLING_RATE / 100
        )
    }

    @Test
    fun `𝕄 inject tracing header 𝕎 intercept() {global known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        stubChain(mockChain, statusCode)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `𝕄 inject non-tracing header 𝕎 intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
        }
    }

    @Test
    fun `𝕄 inject non-tracing b3multi header 𝕎 intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
    fun `𝕄 inject non-tracing b3 header 𝕎 intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
    fun `𝕄 inject non-tracing tracecontext header 𝕎 intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
            assertThat(lastValue.header(TracingInterceptor.W3C_TRACEPARENT_KEY))
                .isEqualTo(
                    "00-%s-%s-00".format(
                        mockSpan.context().toTraceId(),
                        mockSpan.context().toSpanId()
                    )
                )
        }
    }

    @Test
    fun `𝕄 inject tracing header 𝕎 intercept() {local known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
        stubChain(mockChain, statusCode)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `𝕄 inject non-tracing header 𝕎 intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
        }
    }

    @Test
    fun `𝕄 inject non-tracing b3multi header 𝕎 intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.B3MULTI
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { mockLocalTracer }
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
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
    fun `𝕄 inject non-tracing b3 header 𝕎 intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.B3
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { mockLocalTracer }
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
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
    fun `𝕄 inject non-tracing tracecontext header 𝕎 intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.TRACECONTEXT
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { mockLocalTracer }
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.W3C_TRACEPARENT_KEY))
                .isEqualTo(
                    "00-%s-%s-00".format(
                        mockSpan.context().toTraceId(),
                        mockSpan.context().toSpanId()
                    )
                )
        }
    }

    @Test
    fun `𝕄 inject tracing header 𝕎 intercept() for request with parent span`(
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
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
        verify(mockSpanBuilder).asChildOf(parentSpanContext)
    }

    @Test
    fun `𝕄 replace existing tracing header 𝕎 intercept() {global known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) value: String,
        @StringForgery(type = StringForgeryType.ALPHABETICAL) previousValue: String,
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        fakeRequest = fakeRequest.newBuilder().addHeader(key, previousValue).build()
        stubChain(mockChain, statusCode)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `𝕄 replace existing tracing header 𝕎 intercept() {local known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @StringForgery(type = StringForgeryType.ALPHABETICAL) previousValue: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge).newBuilder().addHeader(key, previousValue).build()
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
        stubChain(mockChain, statusCode)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `𝕄 ignore inject exception 𝕎 intercept() {IllegalStateException}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery message: String,
        forge: Forge
    ) {
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
        stubChain(mockChain, statusCode)
        doThrow(IllegalStateException(message)).whenever(mockTracer)
            .inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers().size()).isZero()
        }
    }

    @Test
    fun `𝕄 update header with parent context 𝕎 intercept() for request with tracing headers`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val parentSpanContext: SpanContext = mock()
        whenever(mockTracer.extract<TextMapExtract>(any(), any())) doReturn parentSpanContext
        whenever(mockSpanBuilder.asChildOf(any<SpanContext>())) doReturn mockSpanBuilder
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
        verify(mockSpanBuilder).asChildOf(parentSpanContext)
    }

    @Test
    fun `𝕄 respect sampling decision 𝕎 intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
            setOf(
                TracingHeaderType.DATADOG
            )
        )
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
        whenever(mockTraceSampler.sample()).thenReturn(false)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

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
    fun `𝕄 respect b3multi sampling decision 𝕎 intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
            setOf(
                TracingHeaderType.B3MULTI
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY,
                PrioritySampling.SAMPLER_KEEP.toString()
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample()).thenReturn(false)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

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
    fun `𝕄 respect b3 sampling decision 𝕎 intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
            setOf(
                TracingHeaderType.B3
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3_HEADER_KEY,
                forge.aStringMatching("[a-f0-9]{32}\\-[a-f0-9]{16}\\-1")
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample()).thenReturn(false)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

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
    fun `𝕄 respect tracecontext sampling decision 𝕎 intercept() {sampled in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
            setOf(
                TracingHeaderType.TRACECONTEXT
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.W3C_TRACEPARENT_KEY,
                forge.aStringMatching("00-[a-f0-9]{32}\\-[a-f0-9]{16}\\-01")
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample()).thenReturn(false)
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

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
    fun `𝕄 respect sampling decision 𝕎 intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
        whenever(mockTraceSampler.sample()).thenReturn(true)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER))
                .isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TRACE_ID_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
        }
    }

    @Test
    fun `𝕄 respect b3multi sampling decision 𝕎 intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
            setOf(
                TracingHeaderType.B3MULTI
            )
        )

        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY,
                PrioritySampling.SAMPLER_DROP.toString()
            )
        }
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample()).thenReturn(true)

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
    fun `𝕄 respect b3 sampling decision 𝕎 intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
        whenever(mockTraceSampler.sample()).thenReturn(true)

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
    fun `𝕄 respect tracecontext sampling decision 𝕎 intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(HttpUrl.get(fakeUrl))).thenReturn(
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
        whenever(mockTraceSampler.sample()).thenReturn(true)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(lastValue.header(TracingInterceptor.W3C_TRACEPARENT_KEY))
                .isEqualTo(
                    "00-%s-%s-00".format(
                        mockSpan.context().toTraceId(),
                        mockSpan.context().toSpanId()
                    )
                )
        }
    }

    @Test
    fun `𝕄 create a span with info 𝕎 intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 create a span with info 𝕎 intercept() { resource url with no query paramaters }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        val fakeUrlWithoutQueryParams = forgeUrlWithoutQueryParams(forge)
        fakeRequest = forgeRequest(forge, fakeUrlWithoutQueryParams)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrlWithoutQueryParams)))
            .thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrlWithoutQueryParams)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 create a span with info 𝕎 intercept() for failing request {4xx}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 create a span with info 𝕎 intercept() for failing request {5xx}`(
        @IntForgery(min = 500, max = 600) statusCode: Int
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 create a span with info 𝕎 intercept() for 404 request`() {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, 404)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", 404)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 create a span with info 𝕎 intercept() for throwing request`(
        @Forgery throwable: Throwable
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).finish()
    }

    @Test
    fun `𝕄 warn the user 𝕎 intercept() no tracer registered and TracingFeature not initialized`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        Datadog.globalSdkCore = NoOpSdkCore()
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        testedInterceptor.intercept(mockChain)

        verifyZeroInteractions(mockLocalTracer)
        verifyZeroInteractions(mockTracer)
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                TracingInterceptor.WARNING_TRACING_DISABLED
            )
    }

    @Test
    fun `𝕄 create a span with automatic tracer 𝕎 intercept() if no tracer registered`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val localSpanBuilder: Tracer.SpanBuilder = mock()
        val localSpan: Span = mock()
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(localSpanBuilder.asChildOf(null as SpanContext?)) doReturn localSpanBuilder
        whenever(localSpanBuilder.start()) doReturn localSpan
        whenever(localSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        val response = testedInterceptor.intercept(mockChain)

        verify(localSpan).setTag("http.url", fakeUrl)
        verify(localSpan).setTag("http.method", fakeMethod)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(localSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                TracingInterceptor.WARNING_DEFAULT_TRACER
            )
    }

    @Test
    fun `𝕄 drop automatic tracer 𝕎 intercept() and global tracer registered`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val localSpanBuilder: Tracer.SpanBuilder = mock()
        val localSpan: Span = mock()
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(localSpanBuilder.asChildOf(null as SpanContext?)) doReturn localSpanBuilder
        whenever(localSpanBuilder.start()) doReturn localSpan
        whenever(localSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        val response1 = testedInterceptor.intercept(mockChain)
        val expectedResponse1 = fakeResponse
        GlobalTracer.registerIfAbsent(mockTracer)
        stubChain(mockChain, statusCode)
        val response2 = testedInterceptor.intercept(mockChain)
        val expectedResponse2 = fakeResponse

        verify(localSpan).setTag("http.url", fakeUrl)
        verify(localSpan).setTag("http.method", fakeMethod)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(localSpan).finish()
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response1).isSameAs(expectedResponse1)
        assertThat(response2).isSameAs(expectedResponse2)
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                TracingInterceptor.WARNING_DEFAULT_TRACER
            )
    }

    @Test
    fun `𝕄 call the listener 𝕎 intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery tagKey: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 call the listener 𝕎 intercept() for failing request`(
        @IntForgery(min = 400, max = 600) statusCode: Int,
        @StringForgery tagKey: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 not call the listener 𝕎 intercept() for completed request { not sampled }`(
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verifyZeroInteractions(mockRequestListener)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 call the listener 𝕎 intercept() for throwing request`(
        @Forgery throwable: Throwable,
        @StringForgery tagKey: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
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

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
    }

    @Test
    fun `𝕄 not call the listener 𝕎 intercept() for throwing request { not sampled }`(
        @Forgery throwable: Throwable
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verifyZeroInteractions(mockRequestListener)
    }

    @Test
    fun `M do not update the hostDetector W host list provided`(forge: Forge) {
        // GIVEN
        val localHosts =
            forge.aMap { forge.aStringMatching(HOSTNAME_PATTERN) to setOf(TracingHeaderType.DATADOG) }

        // WHEN
        testedInterceptor = instantiateTestedInterceptor(localHosts) { mockLocalTracer }

        // THEN
        verify(mockResolver, never()).addKnownHostsWithHeaderTypes(localHosts)
    }

    @Test
    fun `𝕄 do nothing 𝕎 intercept() for request with unknown host`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(false)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verifyZeroInteractions(mockTracer, mockLocalTracer)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 warn 𝕎 init() with no known host`() {
        // GIVEN
        whenever(mockResolver.isEmpty()).thenReturn(true)

        // WHEN
        testedInterceptor = instantiateTestedInterceptor { mockLocalTracer }

        verifyZeroInteractions(mockTracer, mockLocalTracer)
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                TracingInterceptor.WARNING_TRACING_NO_HOSTS
            )
    }

    @Test
    fun `𝕄 create only one local tracer 𝕎 intercept() called from multiple threads`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        var called = 0
        testedInterceptor = instantiateTestedInterceptor {
            called++
            mockLocalTracer
        }
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        whenever(mockResolver.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        // need this setup, otherwise #intercept actually throws NPE, which pollutes the log
        val localSpanBuilder: DDTracer.DDSpanBuilder = mock()
        val localSpan: Span = mock(extraInterfaces = arrayOf(MutableSpan::class))
        whenever(localSpanBuilder.asChildOf(null as SpanContext?)) doReturn localSpanBuilder
        whenever(localSpanBuilder.start()) doReturn localSpan
        whenever(localSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
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
        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockLocalTracer, times(2)).buildSpan(TracingInterceptor.SPAN_NAME)
        assertThat(called).isEqualTo(1)
    }

    // region Internal

    private fun stubChain(chain: Interceptor.Chain, statusCode: Int) {
        fakeResponse = forgeResponse(statusCode)

        whenever(chain.request()) doReturn fakeRequest
        whenever(chain.proceed(any())) doReturn fakeResponse
    }

    private fun forgeUrlWithoutQueryParams(forge: Forge, knownHost: String? = null): String {
        val protocol = forge.anElementFrom("http", "https")
        val host = knownHost ?: forge.aStringMatching(HOSTNAME_PATTERN)
        val path = forge.anAlphaNumericalString()
        return "$protocol://$host/$path"
    }

    private fun forgeUrlWithQueryParams(forge: Forge, knownHost: String? = null): String {
        fakeBaseUrl = forgeUrlWithoutQueryParams(forge, knownHost)
        val fakeQueryParams = forge.aList(forge.anInt(min = 0, max = 5)) {
            "${forge.anAlphabeticalString()}=${forge.anAlphabeticalString()}"
        }.joinToString("&")
        return "$fakeBaseUrl?$fakeQueryParams"
    }

    private fun forgeRequest(
        forge: Forge,
        url: String = fakeUrl,
        configure: (Request.Builder) -> Unit = {}
    ): Request {
        val builder = Request.Builder().url(url)
        if (forge.aBool()) {
            fakeMethod = "POST"
            fakeBody = forge.anAlphabeticalString()
            builder.post(RequestBody.create(null, fakeBody!!.toByteArray()))
        } else {
            fakeMethod = forge.anElementFrom("GET", "HEAD", "DELETE")
            fakeBody = null
            builder.method(fakeMethod, null)
        }

        configure(builder)

        return builder.build()
    }

    private fun forgeResponse(statusCode: Int): Response {
        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
            .body(ResponseBody.create(fakeMediaType, fakeResponseBody))
        return builder.build()
    }

    // endregion

    companion object {
        const val HOSTNAME_PATTERN = "([a-z][a-z0-9_~-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"

        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature)
        }
    }
}
