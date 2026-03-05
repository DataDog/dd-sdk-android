/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.rum.internal.net.verifyReportInstrumentationError
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RumInstrumentationOkHttpAdapterTest {

    private lateinit var testedInterceptor: RumInstrumentationOkHttpAdapter

    @Mock
    lateinit var mockRumNetworkInstrumentation: RumNetworkInstrumentation

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRegistry: RequestTracingStateRegistry

    @Mock
    lateinit var mockDistributedTracingInstrumentation: ApmNetworkInstrumentation

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    private lateinit var fakeRequest: Request
    private lateinit var fakeUuid: UUID

    @BeforeEach
    fun `set up`() {
        fakeUuid = UUID.randomUUID()
        fakeRequest = Request.Builder().url(fakeUrl).get().build()
        whenever(mockChain.call()) doReturn mockCall
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockCall.request()) doReturn fakeRequest
        whenever(mockRumNetworkInstrumentation.sdkCore) doReturn mockSdkCore
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<Request>(1)
            request.newBuilder().tag(UUID::class.java, fakeUuid).build()
        }

        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            null
        )
    }

    @Test
    fun `M start and stop resource W intercept() { successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockRumNetworkInstrumentation).startResource(any<OkHttpRequestInfo>())
        verify(mockRumNetworkInstrumentation).stopResource(
            any<OkHttpRequestInfo>(),
            any<OkHttpHttpResponseInfo>(),
            any()
        )
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M start resource and stop with error W intercept() { chain throws }`() {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeException = IOException("network error")
        whenever(mockChain.proceed(any())) doThrow fakeException

        // When / Then
        val thrown = assertThrows<IOException> {
            testedInterceptor.intercept(mockChain)
        }
        assertThat(thrown).isSameAs(fakeException)
        verify(mockRumNetworkInstrumentation).startResource(any<OkHttpRequestInfo>())
        verify(mockRumNetworkInstrumentation).stopResourceWithError(
            any<OkHttpRequestInfo>(),
            any<Throwable>()
        )
    }

    @Test
    fun `M report error and proceed W intercept() { registry returns null }`() {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())) doReturn null
        val fakeResponse = forgeResponse(200)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        mockRumNetworkInstrumentation.verifyReportInstrumentationError(OKHTTP_REQUEST_INFO_IS_MISSED_MESSAGE)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M report error and proceed W intercept() { mergeTagsToRequest returns null }`() {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())) doReturn null
        val fakeResponse = forgeResponse(200)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        mockRumNetworkInstrumentation.verifyReportInstrumentationError(OKHTTP_REQUEST_INFO_IS_MISSED_MESSAGE)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M proceed with chain request W intercept() { registry returns null }`() {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())) doReturn null
        val fakeResponse = forgeResponse(200)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue).isSameAs(fakeRequest)
        }
    }

    @Test
    fun `M proceed with chain request with UUID tag W intercept() { successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.url.toString()).isEqualTo(fakeRequest.url.toString())
            assertThat(firstValue.tag(UUID::class.java)).isEqualTo(fakeUuid)
        }
    }

    @Test
    fun `M pass attributes from registry to stopResource W intercept() { successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockRumNetworkInstrumentation).stopResource(
                any<OkHttpRequestInfo>(),
                any<OkHttpHttpResponseInfo>(),
                capture()
            )
            // state has no span, so toAttributesMap returns empty map
            assertThat(firstValue).isEmpty()
        }
    }

    @Test
    fun `M pass empty attributes W intercept() { registry returns null on second get }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        var callCount = 0
        whenever(mockRegistry.get(mockCall)).thenAnswer {
            callCount++
            if (callCount == 1) fakeState else null
        }
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockRumNetworkInstrumentation).stopResource(
                any<OkHttpRequestInfo>(),
                any<OkHttpHttpResponseInfo>(),
                capture()
            )
            assertThat(firstValue).isEmpty()
        }
    }

    // region upstream interceptor data preservation

    @Test
    fun `M preserve upstream headers W intercept() { chain request has extra headers }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery headerValue: String
    ) {
        // Given
        val upstreamRequest = fakeRequest.newBuilder()
            .addHeader("X-Custom-Auth", headerValue)
            .build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.header("X-Custom-Auth")).isEqualTo(headerValue)
            assertThat(firstValue.tag(UUID::class.java)).isEqualTo(fakeUuid)
        }
    }

    @Test
    fun `M preserve upstream tags W intercept() { chain request has typed tag }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery tagValue: String
    ) {
        // Given
        val upstreamRequest = fakeRequest.newBuilder()
            .tag(String::class.java, tagValue)
            .build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.tag(String::class.java)).isEqualTo(tagValue)
            assertThat(firstValue.tag(UUID::class.java)).isEqualTo(fakeUuid)
        }
    }

    @Test
    fun `M preserve upstream URL W intercept() { chain request has modified URL }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") modifiedUrl: String
    ) {
        // Given
        val upstreamRequest = Request.Builder().url(modifiedUrl).get().build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.url.toString()).isEqualTo(modifiedUrl)
            assertThat(firstValue.tag(UUID::class.java)).isEqualTo(fakeUuid)
        }
    }

    @Test
    fun `M preserve upstream body W intercept() { chain request has body }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery bodyContent: String
    ) {
        // Given
        val upstreamRequest = fakeRequest.newBuilder()
            .post(bodyContent.toRequestBody())
            .build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
            .addTag(UUID::class.java, fakeUuid)
        val fakeState = RequestTracingState(requestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.body).isNotNull
            assertThat(firstValue.method).isEqualTo("POST")
            assertThat(firstValue.tag(UUID::class.java)).isEqualTo(fakeUuid)
        }
    }

    // endregion

    @Test
    fun `M call onRequest W intercept() { distributedTracingInstrumentation, successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        val fakeDistributedTracingState = RequestTracingState(
            requestInfoBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
                .addTag(UUID::class.java, fakeUuid)
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn fakeDistributedTracingState
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockDistributedTracingInstrumentation).onRequest(isA<OkHttpRequestInfo>())
    }

    @Test
    fun `M proceed with modified request W intercept() { distributedTracingInstrumentation adds headers }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeTraceId: String
    ) {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        val fakeDistributedTracingState = RequestTracingState(
            requestInfoBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
                .addTag(UUID::class.java, fakeUuid)
                .addHeader("x-datadog-trace-id", fakeTraceId)
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn fakeDistributedTracingState
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.header("x-datadog-trace-id")).isEqualTo(fakeTraceId)
        }
    }

    @Test
    fun `M call onResponseSucceeded W intercept() { distributedTracingInstrumentation, successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        val fakeDistributedTracingState = RequestTracingState(
            requestInfoBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
                .addTag(UUID::class.java, fakeUuid)
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn fakeDistributedTracingState
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockDistributedTracingInstrumentation).onResponseSucceeded(
            eq(fakeDistributedTracingState),
            isA<OkHttpHttpResponseInfo>()
        )
    }

    @Test
    fun `M call onResponseFailed W intercept() { distributedTracingInstrumentation, chain throws }`() {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        val fakeDistributedTracingState = RequestTracingState(
            requestInfoBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
                .addTag(UUID::class.java, fakeUuid)
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn fakeDistributedTracingState
        val fakeException = IOException("network error")
        whenever(mockChain.proceed(any())) doThrow fakeException

        // When / Then
        val thrown = assertThrows<IOException> {
            testedInterceptor.intercept(mockChain)
        }
        assertThat(thrown).isSameAs(fakeException)
        verify(mockDistributedTracingInstrumentation).onResponseFailed(
            eq(fakeDistributedTracingState),
            eq(fakeException)
        )
    }

    @Test
    fun `M not call onResponseSucceeded W intercept() { distributedTracingInstrumentation, onRequest returns null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn null
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockDistributedTracingInstrumentation, never()).onResponseSucceeded(any(), any())
    }

    @Test
    fun `M not call onResponseFailed W intercept() { distributedTracingInstrumentation, onRequest = null, throws }`() {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn null
        whenever(mockChain.proceed(any())) doThrow IOException("network error")

        // When
        assertThrows<IOException> {
            testedInterceptor.intercept(mockChain)
        }

        // Then
        verify(mockDistributedTracingInstrumentation, never()).onResponseFailed(any(), any())
    }

    @Test
    fun `M use original request W intercept() { distributedTracingInstrumentation, onRequest returns null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        testedInterceptor = RumInstrumentationOkHttpAdapter(
            mockRegistry,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(any())) doReturn null
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.url.toString()).isEqualTo(fakeRequest.url.toString())
            assertThat(firstValue.tag(UUID::class.java)).isEqualTo(fakeUuid)
        }
    }

    private fun forgeResponse(statusCode: Int): Response {
        return Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("OK")
            .build()
    }

    companion object {
        private const val OKHTTP_REQUEST_INFO_IS_MISSED_MESSAGE = "OkHttp request is missed"
    }
}
