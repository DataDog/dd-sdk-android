/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.core.SdkReference
import com.datadog.android.internal.network.HttpSpec
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class ApmInstrumentationOkHttpAdapterTest {

    private lateinit var testedInterceptor: ApmInstrumentationOkHttpAdapter

    @Mock
    lateinit var mockApmNetworkInstrumentation: ApmNetworkInstrumentation

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSdkReference: SdkReference

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockRegistry: RequestTracingStateRegistry

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    private lateinit var fakeRequest: Request

    @BeforeEach
    fun `set up`() {
        fakeRequest = Request.Builder().url(fakeUrl).get().build()
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.call()) doReturn mockCall
        whenever(mockApmNetworkInstrumentation.sdkCoreReference) doReturn mockSdkReference
        whenever(mockSdkReference.get()) doReturn mockSdkCore
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any()))
            .thenAnswer { invocation ->
                invocation.getArgument<Request>(1)
            }

        testedInterceptor = ApmInstrumentationOkHttpAdapter(mockApmNetworkInstrumentation, mockRegistry)
    }

    @Test
    fun `M call onRequest and onResponseSucceeded W intercept() { tracing state present }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val modifiedRequest = Request.Builder().url(fakeUrl).addHeader("x-trace", "123").build()
        val modifiedRequestInfo = OkHttpRequestInfo(modifiedRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<OkHttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.originalRequest).isSameAs(fakeRequest)
        }
        verify(mockApmNetworkInstrumentation).onResponseSucceeded(
            eq(fakeTracingState),
            any<OkHttpHttpResponseInfo>()
        )
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M proceed with modified request W intercept() { tracing state with modified request }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val modifiedRequest = Request.Builder().url(fakeUrl).addHeader("x-trace", "123").build()
        val modifiedRequestInfo = OkHttpRequestInfo(modifiedRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue).isSameAs(modifiedRequest)
        }
    }

    @Test
    fun `M proceed with original request W intercept() { tracing state is null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())) doReturn null
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue).isSameAs(fakeRequest)
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M not call onResponseSucceeded W intercept() { tracing state is null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())) doReturn null
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
    }

    @Test
    fun `M call onResponseFailed W intercept() { chain throws with tracing state }`() {
        // Given
        val modifiedRequestInfo = OkHttpRequestInfo(fakeRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeException = IOException("network error")
        whenever(mockChain.proceed(any())) doThrow fakeException

        // When / Then
        val thrown = assertThrows<IOException> {
            testedInterceptor.intercept(mockChain)
        }
        assertThat(thrown).isSameAs(fakeException)
        verify(mockApmNetworkInstrumentation).onResponseFailed(fakeTracingState, fakeException)
    }

    @Test
    fun `M proceed with original request W intercept() { requestInfo is not OkHttpRequestInfo }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val mockRequestBuilder = mockRequestInfoBuilder(null)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
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
    fun `M propagate exception W intercept() { mergeTagsToRequest throws }`() {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())).thenAnswer {
            throw IllegalStateException("test")
        }

        // When / Then
        assertThrows<IllegalStateException> {
            testedInterceptor.intercept(mockChain)
        }
    }

    @Test
    fun `M report instrumentation error W intercept() { restoreUUIDTag returns null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockRegistry.restoreUUIDTag(eq(mockCall), any())) doReturn null
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockApmNetworkInstrumentation).reportInstrumentationError(any())
    }

    @Test
    fun `M proceed with original request W intercept() { onRequest returns null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn null
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue).isSameAs(fakeRequest)
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M not call response handlers W intercept() { onRequest returns null }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn null
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
        verify(mockApmNetworkInstrumentation, never()).onResponseFailed(any(), any())
    }

    @Test
    fun `M store tracing state in registry W intercept() { onRequest returns state }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val modifiedRequestInfo = OkHttpRequestInfo(fakeRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState
        whenever(mockChain.proceed(any())) doReturn forgeResponse(statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(mockRegistry).setTracingState(eq(mockCall), eq(fakeTracingState))
    }

    // region upstream interceptor data preservation

    @Test
    fun `M pass chain request to onRequest W intercept() { upstream adds headers }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery headerValue: String
    ) {
        // Given
        val upstreamRequest = fakeRequest.newBuilder()
            .addHeader("Authorization", headerValue)
            .build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val modifiedRequest = upstreamRequest.newBuilder().addHeader("x-trace", "123").build()
        val modifiedRequestInfo = OkHttpRequestInfo(modifiedRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<OkHttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.originalRequest.header("Authorization")).isEqualTo(headerValue)
        }
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.header("Authorization")).isEqualTo(headerValue)
            assertThat(firstValue.header("x-trace")).isEqualTo("123")
        }
    }

    @Test
    fun `M pass chain request to onRequest W intercept() { upstream adds tags }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery tagValue: String
    ) {
        // Given
        val upstreamRequest = fakeRequest.newBuilder()
            .tag(String::class.java, tagValue)
            .build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val modifiedRequest = upstreamRequest.newBuilder().addHeader("x-trace", "123").build()
        val modifiedRequestInfo = OkHttpRequestInfo(modifiedRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<OkHttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.originalRequest.tag(String::class.java)).isEqualTo(tagValue)
        }
    }

    @Test
    fun `M pass chain request to onRequest W intercept() { upstream changes URL }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") modifiedUrl: String
    ) {
        // Given
        val upstreamRequest = Request.Builder().url(modifiedUrl).get().build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val tracedRequest = upstreamRequest.newBuilder().addHeader("x-trace", "123").build()
        val tracedRequestInfo = OkHttpRequestInfo(tracedRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(tracedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<OkHttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.url).isEqualTo(modifiedUrl)
        }
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.url.toString()).isEqualTo(modifiedUrl)
        }
    }

    @Test
    fun `M pass chain request to onRequest W intercept() { upstream changes body }`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery bodyContent: String
    ) {
        // Given
        val upstreamRequest = fakeRequest.newBuilder()
            .post(bodyContent.toRequestBody())
            .build()
        whenever(mockChain.request()) doReturn upstreamRequest

        val modifiedRequestInfo = OkHttpRequestInfo(upstreamRequest)
        val mockRequestBuilder = mockRequestInfoBuilder(modifiedRequestInfo)
        val fakeTracingState = RequestTracingState(
            requestInfoBuilder = mockRequestBuilder,
            isSampled = true
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn fakeTracingState

        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<OkHttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.originalRequest.method).isEqualTo(HttpSpec.Method.POST)
            assertThat(firstValue.originalRequest.body).isNotNull
        }
    }

    // endregion

    private fun forgeResponse(statusCode: Int): Response {
        return Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("OK")
            .build()
    }

    private fun mockRequestInfoBuilder(requestInfo: OkHttpRequestInfo?): HttpRequestInfoBuilder {
        val mockBuilder = org.mockito.kotlin.mock<HttpRequestInfoBuilder>()
        if (requestInfo != null) {
            whenever(mockBuilder.build()) doReturn requestInfo
        } else {
            whenever(mockBuilder.build()) doReturn org.mockito.kotlin.mock()
        }
        return mockBuilder
    }
}
