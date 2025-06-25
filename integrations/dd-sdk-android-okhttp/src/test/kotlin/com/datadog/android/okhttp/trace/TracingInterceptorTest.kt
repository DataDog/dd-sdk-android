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
import com.datadog.android.internal.telemetry.TracingHeaderTypesSet
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.okhttp.TraceContext
import com.datadog.android.okhttp.TraceContextInjection
import com.datadog.android.okhttp.internal.trace.toInternalTracingHeaderType
import com.datadog.android.okhttp.internal.utils.forge.OkHttpConfigurator
import com.datadog.android.okhttp.trace.TracingInterceptor.Companion.OKHTTP_INTERCEPTOR_HEADER_TYPES
import com.datadog.android.okhttp.trace.TracingInterceptor.Companion.OKHTTP_INTERCEPTOR_SAMPLE_RATE
import com.datadog.android.okhttp.utils.assertj.HeadersAssert.Companion.assertThat
import com.datadog.android.okhttp.utils.config.DatadogSingletonTestConfiguration
import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.okhttp.utils.verifyLog
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTraceId
import com.datadog.android.trace.api.constants.DatadogTracingConstants
import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.impl.DatadogSpanIdConverterAdapter
import com.datadog.android.trace.impl.DatadogTraceIdAdapter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
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
@ForgeConfiguration(OkHttpConfigurator::class)
internal open class TracingInterceptorTest {

    lateinit var testedInterceptor: TracingInterceptor

    // region Mocks

    private lateinit var mockTracer: DatadogTracer

    private lateinit var mockPropagation: DatadogPropagation

    private lateinit var mockSpan: DatadogSpan

    private lateinit var mockSpanContext: DatadogSpanContext

    private lateinit var mockSpanBuilder: DatadogSpanBuilder

    private lateinit var mockLocalTracer: DatadogTracer

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequestListener: TracedRequestListener

    @Mock
    lateinit var mockResolver: DefaultFirstPartyHostHeaderTypeResolver

    lateinit var mockTraceSampler: Sampler<DatadogSpan>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // endregion

    // region Fakes

    private lateinit var fakeMethod: String
    private var fakeBody: String? = null
    private var fakeMediaType: MediaType? = null

    @StringForgery(type = StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String

    lateinit var fakeBaseUrl: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @LongForgery
    var fakeSpanId: Long = 0L

    @StringForgery(regex = "[a-f][0-9]{31}")
    lateinit var fakeTraceIdAsString: String

    lateinit var fakeTraceId: DatadogTraceId

    private var fakeOrigin: String? = null

    lateinit var fakeLocalHosts: Map<String, Set<TracingHeaderType>>

    @BoolForgery
    var fakeRedacted404Resources: Boolean = true

    // endregion

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTraceId = forge.aDatadogTraceId(fakeTraceIdAsString)
        fakeOrigin = forge.aNullable { anAlphabeticalString() }
        mockSpanContext = forge.newSpanContextMock(fakeTraceId, fakeSpanId)
        mockSpan = forge.newSpanMock(mockSpanContext)
        mockSpanBuilder = forge.newSpanBuilderMock(mockSpan, context = mockSpanContext)
        mockTraceSampler = forge.newTraceSamplerMock(mockSpan)
        mockPropagation = newAgentPropagationMock(mockSpanContext)
        mockTracer = forge.newTracerMock(mockSpanBuilder, mockPropagation)
        mockLocalTracer = forge.newTracerMock(mockSpanBuilder, mockPropagation)

        val mediaType = forge.anElementFrom("application", "image", "text", "model") +
            "/" + forge.anAlphabeticalString()
        fakeLocalHosts =
            forge.aMap { forge.aStringMatching(HOSTNAME_PATTERN) to setOf(TracingHeaderType.DATADOG) }
        fakeMediaType = mediaType.toMediaTypeOrNull()
        fakeUrl = forgeUrlWithQueryParams(forge)
        fakeRequest = forgeRequest(forge)
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mock()
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mockResolver
        testedInterceptor = instantiateTestedInterceptor(
            fakeLocalHosts,
            globalTracerProvider = { mockTracer },
            localTracerFactory = { _, _ -> mockLocalTracer }
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
            val span = it.arguments[1] as DatadogSpan
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    open fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>> = emptyMap(),
        globalTracerProvider: () -> DatadogTracer? = { null },
        localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer
    ): TracingInterceptor {
        return TracingInterceptor(
            sdkInstanceName = null,
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            traceOrigin = fakeOrigin,
            traceSampler = mockTraceSampler,
            localTracerFactory = localTracerFactory,
            redacted404ResourceName = fakeRedacted404Resources,
            traceContextInjection = TraceContextInjection.ALL,
            globalTracerProvider = globalTracerProvider
        )
    }

    open fun getExpectedOrigin(): String? {
        return fakeOrigin
    }

    @Test
    fun `M instantiate with default values W init()`() {
        // Given
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mock()

        // When
        val interceptor = TracingInterceptor.Builder(emptyMap()).build()

        // Then
        assertThat(interceptor.tracedHosts).isEmpty()
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(DeterministicTraceSampler::class.java)
        assertThat(interceptor.traceSampler.getSampleRate()).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLE_RATE
        )
    }

    @Test
    fun `M instantiate with default values W init()`(
        @StringForgery(regex = "[a-z]+\\.[a-z]{3}") hosts: List<String>
    ) {
        // Given
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mock()

        // When
        val interceptor = TracingInterceptor.Builder(hosts).build()

        // Then
        assertThat(interceptor.tracedHosts.keys).containsAll(hosts)
        val allHeaderTypes = interceptor.tracedHosts
            .values
            .fold(mutableSetOf<TracingHeaderType>()) { acc, tracingHeaderTypes ->
                acc.apply { this += tracingHeaderTypes }
            }
        assertThat(allHeaderTypes).isEqualTo(
            setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
        )
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(DeterministicTraceSampler::class.java)
        assertThat(interceptor.traceSampler.getSampleRate()).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLE_RATE
        )
    }

    @Test
    fun `M inject tracing header W intercept() {global known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        stubChain(mockChain, statusCode)
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

        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `M inject non-tracing b3multi header W intercept() {global known host + not sampled}`(
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
    fun `M inject datadog headers W intercept() {global known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        val nonDatadogContextKey = forge.anAlphabeticalString()
        val nonDatadogContextKeyValue = forge.anAlphabeticalString()
        val datadogContext = listOf(
            TracingInterceptor.DATADOG_SPAN_ID_HEADER,
            TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER,
            TracingInterceptor.DATADOG_ORIGIN_HEADER,
            TracingInterceptor.DATADOG_TAGS_HEADER
        ).associateWith { forge.anAlphabeticalString() }
        mockPropagation.wheneverInjectCalledPassContextToHeaders(
            datadogContext,
            nonDatadogContextKey,
            nonDatadogContextKeyValue
        )
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
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER)).isEqualTo("0")
            datadogContext.forEach {
                assertThat(lastValue.header(it.key)).isEqualTo(it.value)
            }
            assertThat(lastValue.header(nonDatadogContextKey)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY)).isEqualTo("0")
            assertThat(lastValue.header(TracingInterceptor.B3M_SPAN_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3M_TRACE_ID_KEY)).isNull()
            assertThat(lastValue.header(TracingInterceptor.B3_HEADER_KEY)).isEqualTo("0")
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
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `M inject datadog headers W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        val nonDatadogContextKey = forge.anAlphabeticalString()
        val nonDatadogContextKeyValue = forge.anAlphabeticalString()
        val datadogContext = listOf(
            TracingInterceptor.DATADOG_TAGS_HEADER,
            TracingInterceptor.DATADOG_SPAN_ID_HEADER,
            TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER,
            TracingInterceptor.DATADOG_ORIGIN_HEADER
        ).associateWith { forge.anAlphabeticalString() }

        mockPropagation.wheneverInjectCalledPassContextToHeaders(
            datadogContext,
            nonDatadogContextKey,
            nonDatadogContextKeyValue
        )

        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
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
            datadogContext.forEach {
                assertThat(lastValue.header(it.key)).isEqualTo(it.value)
            }
            assertThat(lastValue.header(nonDatadogContextKey)).isNull()
        }
    }

    @Test
    fun `M inject non-tracing b3multi header W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeLocalHosts =
            forge.aMap { forge.aStringMatching(HOSTNAME_PATTERN) to setOf(TracingHeaderType.B3MULTI) }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
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
        fakeLocalHosts =
            forge.aMap { forge.aStringMatching(HOSTNAME_PATTERN) to setOf(TracingHeaderType.B3) }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
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
        fakeLocalHosts =
            forge.aMap { forge.aStringMatching(HOSTNAME_PATTERN) to setOf(TracingHeaderType.TRACECONTEXT) }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
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
    fun `M inject all non-tracing headers W intercept() {local known host + not sampled}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)).thenReturn(false)
        fakeLocalHosts = forge.aMap {
            forge.aStringMatching(HOSTNAME_PATTERN) to setOf(
                TracingHeaderType.DATADOG,
                TracingHeaderType.B3,
                TracingHeaderType.B3MULTI,
                TracingHeaderType.TRACECONTEXT
            )
        }
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
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
            assertThat(lastValue.header(TracingInterceptor.DATADOG_ORIGIN_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_TAGS_HEADER)).isNull()
            assertThat(lastValue.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)).isNull()
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
    fun `M inject tracing header W intercept() for request with parent span`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        val parentSpanContext = forge.newSpanContextMock<DatadogSpanContext>()
        val parentSpan = forge.newSpanMock(parentSpanContext)

        whenever(mockSpanBuilder.withParentContext(parentSpanContext)) doReturn mockSpanBuilder
        fakeRequest = forgeRequest(forge) { it.tag(DatadogSpan::class.java, parentSpan) }
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
        verify(mockSpanBuilder).withParentContext(parentSpanContext)
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
    }

    @Test
    fun `M inject tracing header W intercept() for request with parent TraceContext`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @Forgery fakeTraceContext: TraceContext,
        forge: Forge
    ) {
        // Given

        val fakeExpectedTraceId = DatadogTraceIdAdapter.fromHex(fakeTraceContext.traceId)
        val fakeExpectedSpanId = DatadogSpanIdConverterAdapter.fromHex(fakeTraceContext.spanId)
        whenever(mockSpanBuilder.withParentContext(any<DatadogSpanContext>())) doReturn mockSpanBuilder

        fakeRequest = forgeRequest(forge) { it.tag(TraceContext::class.java, fakeTraceContext) }
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            if (fakeTraceContext.samplingPriority > 0) {
                assertThat(firstValue.headers(key)).containsOnly(value)
            } else {
                assertThat(firstValue.headers(key)).isEmpty()
            }
        }
        argumentCaptor<DatadogSpanContext> {
            verify(mockSpanBuilder).withParentContext(capture())
            val extractedContext: DatadogSpanContext = firstValue
            assertThat(extractedContext.traceId).isEqualTo(fakeExpectedTraceId)
            assertThat(extractedContext.spanId).isEqualTo(fakeExpectedSpanId)
        }
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
    }

    @Test
    fun `M replace existing tracing header W intercept() {global known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) value: String,
        @StringForgery(type = StringForgeryType.ALPHABETICAL) previousValue: String,
        @IntForgery(min = 200, max = 600) statusCode: Int
    ) {
        fakeRequest = fakeRequest.newBuilder().addHeader(key, previousValue).build()
        stubChain(mockChain, statusCode)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `M replace existing tracing header W intercept() {local known host}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @StringForgery(type = StringForgeryType.ALPHABETICAL) previousValue: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge).newBuilder().addHeader(key, previousValue).build()
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
    }

    @Test
    fun `M ignore inject exception W intercept() {IllegalStateException}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery message: String,
        forge: Forge
    ) {
        fakeUrl = forgeUrlWithQueryParams(forge, forge.anElementFrom(fakeLocalHosts.keys))
        fakeRequest = forgeRequest(forge)
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(false)
        stubChain(mockChain, statusCode)

        doThrow(IllegalStateException(message))
            .whenever(mockPropagation)
            .inject(any<DatadogSpanContext>(), any<Request>(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers.size).isZero()
        }
    }

    @Test
    fun `M update header with parent context W intercept() for request with tracing headers`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val parentSpanContext: DatadogSpanContext = mock()
        whenever(mockPropagation.extract(any<Request>(), any())) doReturn parentSpanContext
        whenever(mockSpanBuilder.withParentContext(any<DatadogSpanContext>())) doReturn mockSpanBuilder
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        mockPropagation.wheneverInjectThenValueToHeaders(key, value)

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.headers(key)).containsOnly(value)
        }
        verify(mockSpanBuilder).withParentContext(parentSpanContext)
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
    }

    @Test
    fun `M respect sampling decision W intercept() {sampled in upstream interceptor}`(
        @StringForgery key: String,
        @IntForgery(min = 200, max = 600) statusCode: Int,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) value: String,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER,
                forge.anElementFrom(
                    DatadogTracingConstants.PrioritySampling.SAMPLER_KEEP.toString(),
                    DatadogTracingConstants.PrioritySampling.USER_KEEP.toString()
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
                DatadogTracingConstants.PrioritySampling.SAMPLER_KEEP.toString()
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
                    DatadogTracingConstants.PrioritySampling.SAMPLER_DROP.toString(),
                    DatadogTracingConstants.PrioritySampling.USER_DROP.toString()
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
    fun `M respect b3multi sampling decision W intercept() {sampled out in upstream interceptor}`(
        @IntForgery(min = 200, max = 600) statusCode: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())).thenReturn(
            setOf(
                TracingHeaderType.B3MULTI
            )
        )
        fakeRequest = forgeRequest(forge) {
            it.addHeader(
                TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY,
                DatadogTracingConstants.PrioritySampling.SAMPLER_DROP.toString()
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
            assertThat(lastValue.header(TracingInterceptor.B3M_SAMPLING_PRIORITY_KEY))
                .isEqualTo("0")
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
                    DatadogTracingConstants.PrioritySampling.SAMPLER_DROP.toString(),
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
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M create a span with info W intercept() { resource url with no query paramaters }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        val fakeUrlWithoutQueryParams = forgeUrlWithoutQueryParams(forge)
        fakeRequest = forgeRequest(forge, fakeUrlWithoutQueryParams)
        whenever(mockResolver.isFirstPartyUrl(fakeUrlWithoutQueryParams.toHttpUrl()))
            .thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).resourceName =
            fakeUrlWithoutQueryParams.lowercase(Locale.US)
        verify(mockSpan).setTag("http.url", fakeUrlWithoutQueryParams.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).finish()
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
        verify(mockSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).isError = true
        verify(mockSpan).finish()
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
        verify(mockSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan, never()).isError = true
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M create a span with info W intercept() for 404 request`() {
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, 404)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", 404)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).isError = true
        if (fakeRedacted404Resources) {
            verify(mockSpan).resourceName = TracingInterceptor.RESOURCE_NAME_404
        } else {
            verify(mockSpan, never()).resourceName = TracingInterceptor.RESOURCE_NAME_404
        }
        verify(mockSpan).finish()
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
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(mockSpan).finish()
    }

    @Test
    fun `M warn the user W intercept() no tracer registered and TracingFeature not initialized`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
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
        val localSpanContext = forge.newSpanContextMock<DatadogSpanContext>(fakeTraceId, fakeSpanId)
        val localSpan: DatadogSpan = forge.newSpanMock(localSpanContext)
        val localSpanBuilder: DatadogSpanBuilder = forge.newSpanBuilderMock(localSpan)

        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(localSpan)).thenReturn(true)
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder
        val testedInterceptorNoGlobal = instantiateTestedInterceptor(
            fakeLocalHosts,
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { null }
        )

        val response = testedInterceptorNoGlobal.intercept(mockChain)

        verify(localSpanBuilder).withOrigin(getExpectedOrigin())
        verify(localSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(localSpan).setTag("http.method", fakeMethod)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(localSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(localSpan).finish()
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
        val localSpan: DatadogSpan = forge.newSpanMock()
        val localSpanBuilder = forge.newSpanBuilderMock(localSpan)

        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)
        whenever(mockTraceSampler.sample(localSpan)).thenReturn(true)
        whenever(mockSpanContext.spanId) doReturn fakeSpanId
        whenever(mockSpanContext.traceId) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder
        val testedInterceptorNoGlobal = instantiateTestedInterceptor(
            fakeLocalHosts,
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { null }
        )

        val response1 = testedInterceptorNoGlobal.intercept(mockChain)
        val expectedResponse1 = fakeResponse
        stubChain(mockChain, statusCode)
        val response2 = testedInterceptor.intercept(mockChain)
        val expectedResponse2 = fakeResponse

        verify(localSpanBuilder).withOrigin(getExpectedOrigin())
        verify(localSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(localSpan).setTag("http.method", fakeMethod)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(localSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(localSpan).finish()
        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).resourceName = fakeBaseUrl.lowercase(Locale.US)
        verify(mockSpan).finish()
        assertThat(response1).isSameAs(expectedResponse1)
        assertThat(response2).isSameAs(expectedResponse2)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            TracingInterceptor.WARNING_DEFAULT_TRACER
        )
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
            val span = it.arguments[1] as DatadogSpan
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
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
            val span = it.arguments[1] as DatadogSpan
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockSpanBuilder).withOrigin(getExpectedOrigin())
        verify(mockSpan).setTag("http.url", fakeUrl.lowercase(Locale.US))
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).setTag("span.kind", "client")
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
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
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
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
        // GIVEN
        whenever(mockResolver.isEmpty()) doReturn true
        whenever(mockResolver.isFirstPartyUrl(any<String>())) doReturn false
        whenever(mockResolver.isFirstPartyUrl(any<HttpUrl>())) doReturn false
        testedInterceptor = instantiateTestedInterceptor { _, _ -> mockLocalTracer }
        stubChain(mockChain, statusCode)

        // WHEN
        testedInterceptor.intercept(mockChain)
        testedInterceptor.intercept(mockChain)

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
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        var called = 0
        testedInterceptor = instantiateTestedInterceptor { _, _ ->
            called++
            mockLocalTracer
        }
        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())).thenReturn(true)
        stubChain(mockChain, statusCode)

        // need this setup, otherwise #intercept actually throws NPE, which pollutes the log
        val localSpanBuilder: DatadogSpanBuilder = mock()
        val localSpan: DatadogSpan = mock()
        whenever(localSpanBuilder.withParentContext(null as DatadogSpanContext?)) doReturn localSpanBuilder
        whenever(localSpanBuilder.start()) doReturn localSpan
        whenever(localSpan.context()) doReturn mockSpanContext
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
        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockLocalTracer, times(2)).buildSpan(TracingInterceptor.SPAN_NAME)
        assertThat(called).isEqualTo(1)
    }

    @Test
    fun `M add trace sample rate and header types to tracing feature context W constructor called`(
        @FloatForgery(min = 0f, max = 100f) sampleRate: Float
    ) {
        // GIVEN
        whenever(mockTraceSampler.getSampleRate()) doReturn sampleRate

        val contextMock = mock<MutableMap<String, Any?>>()
        whenever(rumMonitor.mockSdkCore.updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), any(), any())) doAnswer {
            val updater = it.getArgument<(MutableMap<String, Any?>) -> Unit>(it.arguments.lastIndex)
            updater(contextMock)
        }

        // WHEN
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        // THEN
        verify(contextMock).put(OKHTTP_INTERCEPTOR_SAMPLE_RATE, sampleRate)
        verify(
            contextMock
        ).put(
            OKHTTP_INTERCEPTOR_HEADER_TYPES,
            TracingHeaderTypesSet(fakeLocalHosts.values.flatten().map { it.toInternalTracingHeaderType() }.toSet())
        )
        verifyNoMoreInteractions(contextMock)
    }

    // region Internal

    internal fun stubChain(chain: Interceptor.Chain, statusCode: Int) {
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
            fakeMethod = forge.anElementFrom("POST", "PUT", "PATCH")
            fakeBody = forge.anAlphabeticalString()
            with(builder) {
                val body = fakeBody!!.toByteArray().toRequestBody(null)
                when (fakeMethod) {
                    "POST" -> post(body)
                    "PUT" -> put(body)
                    "PATCH" -> patch(body)
                    else -> {
                        throw IllegalArgumentException("Unknown method value: $fakeMethod")
                    }
                }
            }
        } else {
            fakeMethod = forge.anElementFrom("GET", "HEAD", "DELETE", "CONNECT", "TRACE", "OPTIONS")
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
            .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
            .body(fakeResponseBody.toResponseBody(fakeMediaType))
        return builder.build()
    }

    // endregion

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
