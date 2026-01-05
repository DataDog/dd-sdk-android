/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.core.SdkReference
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.rum.internal.net.verifyReportInstrumentationError
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
import org.mockito.kotlin.mock
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
    lateinit var mockSdkReference: SdkReference

    @Mock
    lateinit var mockRegistry: RequestTracingStateRegistry

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    private lateinit var fakeRequest: Request

    @BeforeEach
    fun `set up`() {
        fakeRequest = Request.Builder().url(fakeUrl).get().build()
        whenever(mockChain.call()) doReturn mockCall
        whenever(mockCall.request()) doReturn fakeRequest
        whenever(mockRumNetworkInstrumentation.sdkCoreReference) doReturn mockSdkReference
        whenever(mockSdkReference.get()) doReturn mockSdkCore
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedInterceptor = RumInstrumentationOkHttpAdapter(mockRumNetworkInstrumentation, mockRegistry)
    }

    @Test
    fun `M start and stop resource W intercept() { successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
        val fakeState = RequestTracingState(tracedRequestInfoBuilder = fakeRequestBuilder)
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
        val fakeState = RequestTracingState(tracedRequestInfoBuilder = fakeRequestBuilder)
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
    fun `M report error and proceed W intercept() { request info is null }`() {
        // Given
        whenever(mockRegistry.get(mockCall)) doReturn null
        val fakeResponse = forgeResponse(200)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        mockRumNetworkInstrumentation.verifyReportInstrumentationError(OKHTTP_REQUEST_INFO_IS_MISSED_MESSAGE)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M report error and proceed W intercept() { request info is not OkHttpRequestInfo }`() {
        // Given
        val mockBuilder = mock<HttpRequestInfoBuilder>()
        whenever(mockBuilder.build()) doReturn mock<HttpRequestInfo>()
        val fakeState = RequestTracingState(tracedRequestInfoBuilder = mockBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(200)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        mockRumNetworkInstrumentation.verifyReportInstrumentationError(OKHTTP_REQUEST_INFO_IS_MISSED_MESSAGE)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `M proceed with original request W intercept() { request info is null }`() {
        // Given
        whenever(mockRegistry.get(mockCall)) doReturn null
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
    fun `M proceed with request from registry W intercept() { successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
        val fakeState = RequestTracingState(tracedRequestInfoBuilder = fakeRequestBuilder)
        whenever(mockRegistry.get(mockCall)) doReturn fakeState
        val fakeResponse = forgeResponse(statusCode)
        whenever(mockChain.proceed(any())) doReturn fakeResponse

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())
            assertThat(firstValue.url.toString()).isEqualTo(fakeRequest.url.toString())
        }
    }

    @Test
    fun `M pass attributes from registry to stopResource W intercept() { successful response }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeRequestBuilder = OkHttpRequestInfoBuilder(fakeRequest.newBuilder())
        val fakeState = RequestTracingState(tracedRequestInfoBuilder = fakeRequestBuilder)
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
