/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.trace.NetworkTracingInstrumentation
import com.datadog.android.trace.internal.net.RequestTraceState
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.nio.ByteBuffer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogRequestCallbackTest {

    private lateinit var testedCallback: DatadogRequestCallback

    @Mock
    lateinit var mockDelegate: UrlRequest.Callback

    @Mock
    lateinit var mockNetworkTracingInstrumentation: NetworkTracingInstrumentation

    @Mock
    lateinit var mockUrlRequest: UrlRequest

    @Mock
    lateinit var mockUrlResponseInfo: UrlResponseInfo

    @Mock
    lateinit var mockRequestModifier: HttpRequestInfoModifier

    @Mock
    lateinit var mockByteBuffer: ByteBuffer

    @Mock
    lateinit var mockCronetException: CronetException

    @BeforeEach
    fun `set up`() {
        testedCallback = DatadogRequestCallback(mockDelegate, mockNetworkTracingInstrumentation)
    }

    @Test
    fun `M delegate onRedirectReceived W onRedirectReceived()`(
        @StringForgery fakeNewLocationUrl: String
    ) {
        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)
    }

    @Test
    fun `M delegate onResponseStarted W onResponseStarted()`() {
        // When
        testedCallback.onResponseStarted(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onResponseStarted(mockUrlRequest, mockUrlResponseInfo)
    }

    @Test
    fun `M delegate onReadCompleted W onReadCompleted()`() {
        // When
        testedCallback.onReadCompleted(mockUrlRequest, mockUrlResponseInfo, mockByteBuffer)

        // Then
        verify(mockDelegate).onReadCompleted(mockUrlRequest, mockUrlResponseInfo, mockByteBuffer)
    }

    @Test
    fun `M delegate onSucceeded and call traceInstrumentation W onSucceeded() {traceState set}`(
        @IntForgery(min = 100, max = 599) fakeStatusCode: Int,
        @StringForgery fakeUrl: String
    ) {
        // Given
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        val fakeTracingState = RequestTraceState(mockRequestModifier, isSampled = true)
        testedCallback.traceState = fakeTracingState

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
        argumentCaptor<CronetHttpResponseInfo> {
            verify(mockNetworkTracingInstrumentation).onResponseSucceed(any(), capture())
            org.assertj.core.api.Assertions.assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
    }

    @Test
    fun `M only delegate onSucceeded W onSucceeded() {traceState null}`() {
        // Given
        testedCallback.traceState = null

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
        verifyNoInteractions(mockNetworkTracingInstrumentation)
    }

    @Test
    fun `M only delegate onSucceeded W onSucceeded() {traceInstrumentation null}`() {
        // Given
        testedCallback = DatadogRequestCallback(mockDelegate, null)
        val fakeTracingState = RequestTraceState(mockRequestModifier, isSampled = true)
        testedCallback.traceState = fakeTracingState

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
    }

    @Test
    fun `M delegate onFailed and call traceInstrumentation W onFailed() {traceState set}`() {
        // Given
        val fakeTracingState = RequestTraceState(mockRequestModifier, isSampled = true)
        testedCallback.traceState = fakeTracingState

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
        verify(mockNetworkTracingInstrumentation).onResponseFailed(fakeTracingState, mockCronetException)
    }

    @Test
    fun `M delegate onFailed with IOException W onFailed() {traceState set, error null}`() {
        // Given
        val fakeTracingState = RequestTraceState(mockRequestModifier, isSampled = true)
        testedCallback.traceState = fakeTracingState

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, null)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, null)
        argumentCaptor<Throwable> {
            verify(mockNetworkTracingInstrumentation).onResponseFailed(any(), capture())
            assertThat(firstValue).isInstanceOf(IOException::class.java)
            assertThat(firstValue.message).isEqualTo("Response failed")
        }
    }

    @Test
    fun `M only delegate onFailed W onFailed() {traceState null}`() {
        // Given
        testedCallback.traceState = null

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
        verifyNoInteractions(mockNetworkTracingInstrumentation)
    }

    @Test
    fun `M only delegate onFailed W onFailed() {traceInstrumentation null}`() {
        // Given
        testedCallback = DatadogRequestCallback(mockDelegate, null)
        val fakeTracingState = RequestTraceState(mockRequestModifier, isSampled = true)
        testedCallback.traceState = fakeTracingState

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
    }

    @Test
    fun `M store and retrieve traceState W traceState property`() {
        // Given
        val fakeTracingState = RequestTraceState(mockRequestModifier, isSampled = true)

        // When
        testedCallback.traceState = fakeTracingState

        // Then
        assertThat(testedCallback.traceState).isSameAs(fakeTracingState)
    }
}
