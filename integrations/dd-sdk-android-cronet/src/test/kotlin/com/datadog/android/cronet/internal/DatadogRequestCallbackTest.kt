/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.cronet.internal.DatadogRequestCallback.Companion.FOLLOW_REDIRECT_IS_NOT_DETECTED_MESSAGE
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    lateinit var mockApmNetworkInstrumentation: ApmNetworkInstrumentation

    @Mock
    lateinit var mockUrlRequest: UrlRequest

    @Mock
    lateinit var mockUrlResponseInfo: UrlResponseInfo

    @Mock
    lateinit var mockRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockByteBuffer: ByteBuffer

    @Mock
    lateinit var mockCronetException: CronetException

    @Mock
    lateinit var mockTraceState: RequestTraceState

    @BeforeEach
    fun `set up`() {
        testedCallback = DatadogRequestCallback(mockDelegate, mockApmNetworkInstrumentation)
    }

    // region onRedirectReceived

    @Test
    fun `M delegate onRedirectReceived with wrapped request W onRedirectReceived() {DETAILED scope}`(
        @StringForgery fakeNewLocationUrl: String,
        @IntForgery(min = 300, max = 399) fakeStatusCode: Int,
        @StringForgery fakeUrl: String
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn ApmNetworkTracingScope.DETAILED
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        argumentCaptor<UrlRequest> {
            verify(mockDelegate).onRedirectReceived(capture(), any(), any())
            assertThat(firstValue).isInstanceOf(RedirectTrackingUrlRequest::class.java)
            assertThat((firstValue as RedirectTrackingUrlRequest).delegate).isSameAs(mockUrlRequest)
        }
    }

    @Test
    fun `M finish current span W onRedirectReceived() {DETAILED scope, traceState set}`(
        @IntForgery(min = 300, max = 399) fakeStatusCode: Int,
        @StringForgery fakeUrl: String,
        @StringForgery fakeNewLocationUrl: String
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn ApmNetworkTracingScope.DETAILED
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        argumentCaptor<CronetHttpResponseInfo> {
            verify(mockApmNetworkInstrumentation).onResponseSucceeded(any(), capture())
            assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
    }

    @Test
    fun `M delegate without tracing W onRedirectReceived() {APPLICATION_LEVEL scope}`(
        @StringForgery fakeNewLocationUrl: String
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn
            ApmNetworkTracingScope.APPLICATION_LEVEL_REQUESTS_ONLY

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
        verify(mockApmNetworkInstrumentation, never()).onResponseFailed(any(), any())
    }

    @Test
    fun `M not call onResponseSucceeded W onRedirectReceived() {traceState null, DETAILED scope}`(
        @StringForgery fakeNewLocationUrl: String
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn ApmNetworkTracingScope.DETAILED

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(
            isA<RedirectTrackingUrlRequest>(),
            eq(mockUrlResponseInfo),
            eq(fakeNewLocationUrl)
        )
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
    }

    @Test
    fun `M create redirect span W onRedirectReceived() {DETAILED, MutableHttpRequestInfo, follows redirect}`(
        @StringForgery fakeNewLocationUrl: String,
        @IntForgery(min = 300, max = 399) fakeStatusCode: Int,
        @StringForgery fakeUrl: String
    ) {
        // Given
        val mockMutableRequestInfo: HttpRequestInfo = mock(
            extraInterfaces = arrayOf(MutableHttpRequestInfo::class)
        )
        val mockRequestInfoBuilder: HttpRequestInfoBuilder = mock()
        val mockRedirectRequestInfo: HttpRequestInfo = mock()
        val mockRedirectTraceState: RequestTraceState = mock()

        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn ApmNetworkTracingScope.DETAILED
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        whenever(mockApmNetworkInstrumentation.onRequest(mockMutableRequestInfo)) doReturn mockTraceState
        whenever((mockMutableRequestInfo as MutableHttpRequestInfo).newBuilder()) doReturn mockRequestInfoBuilder
        whenever(mockRequestInfoBuilder.setUrl(fakeNewLocationUrl)) doReturn mockRequestInfoBuilder
        whenever(mockRequestInfoBuilder.build()) doReturn mockRedirectRequestInfo
        whenever(mockApmNetworkInstrumentation.onRequest(mockRedirectRequestInfo)) doReturn mockRedirectTraceState
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(mockMutableRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockApmNetworkInstrumentation).onResponseSucceeded(any(), any())
        verify(mockApmNetworkInstrumentation).removeTracingHeaders(mockRequestInfoBuilder)
        verify(mockRequestInfoBuilder).setUrl(fakeNewLocationUrl)
        verify(mockApmNetworkInstrumentation).onRequest(mockRedirectRequestInfo)
    }

    @Test
    fun `M report error W onRedirectReceived() {DETAILED, MutableHttpRequestInfo, no followRedirect}`(
        @StringForgery fakeNewLocationUrl: String,
        @IntForgery(min = 300, max = 399) fakeStatusCode: Int,
        @StringForgery fakeUrl: String
    ) {
        // Given
        val mockMutableRequestInfo: HttpRequestInfo = mock(
            extraInterfaces = arrayOf(MutableHttpRequestInfo::class)
        )

        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn ApmNetworkTracingScope.DETAILED
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        whenever(mockApmNetworkInstrumentation.onRequest(mockMutableRequestInfo)) doReturn mockTraceState

        testedCallback.onRequestStarted(mockMutableRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockApmNetworkInstrumentation).reportInstrumentationError(FOLLOW_REDIRECT_IS_NOT_DETECTED_MESSAGE)
        verify(mockApmNetworkInstrumentation, never()).removeTracingHeaders(any())
    }

    @Test
    fun `M not create redirect span W onRedirectReceived() {DETAILED, non-MutableHttpRequestInfo}`(
        @StringForgery fakeNewLocationUrl: String,
        @IntForgery(min = 300, max = 399) fakeStatusCode: Int,
        @StringForgery fakeUrl: String
    ) {
        // Given
        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn ApmNetworkTracingScope.DETAILED
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        whenever(mockApmNetworkInstrumentation.onRequest(mockRequestInfo)) doReturn mockTraceState
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockApmNetworkInstrumentation).onResponseSucceeded(any(), any())
        verify(mockApmNetworkInstrumentation, never()).removeTracingHeaders(any())
    }

    @Test
    fun `M only delegate onRedirectReceived W onRedirectReceived() {apmNetworkInstrumentation null}`(
        @StringForgery fakeNewLocationUrl: String
    ) {
        // Given
        testedCallback = DatadogRequestCallback(mockDelegate, null)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)
    }

    // endregion

    // region onResponseStarted

    @Test
    fun `M delegate onResponseStarted W onResponseStarted()`() {
        // When
        testedCallback.onResponseStarted(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onResponseStarted(mockUrlRequest, mockUrlResponseInfo)
    }

    // endregion

    // region onReadCompleted

    @Test
    fun `M delegate onReadCompleted W onReadCompleted()`() {
        // When
        testedCallback.onReadCompleted(mockUrlRequest, mockUrlResponseInfo, mockByteBuffer)

        // Then
        verify(mockDelegate).onReadCompleted(mockUrlRequest, mockUrlResponseInfo, mockByteBuffer)
    }

    // endregion

    // region onSucceeded

    @Test
    fun `M delegate onSucceeded and call apmNetworkInstrumentation W onSucceeded() {traceState set}`(
        @IntForgery(min = 100, max = 599) fakeStatusCode: Int,
        @StringForgery fakeUrl: String
    ) {
        // Given
        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockUrlResponseInfo.url) doReturn fakeUrl
        whenever(mockUrlResponseInfo.allHeaders) doReturn emptyMap()
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
        argumentCaptor<CronetHttpResponseInfo> {
            verify(mockApmNetworkInstrumentation).onResponseSucceeded(any(), capture())
            assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
    }

    @Test
    fun `M only delegate onSucceeded W onSucceeded() {traceState null}`() {
        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
        verifyNoInteractions(mockApmNetworkInstrumentation)
    }

    @Test
    fun `M only delegate onSucceeded W onSucceeded() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = DatadogRequestCallback(mockDelegate, null)

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
    }

    // endregion

    // region onFailed

    @Test
    fun `M delegate onFailed and call apmNetworkInstrumentation W onFailed() {traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
        verify(mockApmNetworkInstrumentation).onResponseFailed(mockTraceState, mockCronetException)
    }

    @Test
    fun `M delegate onFailed with IOException W onFailed() {traceState set, error null}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, null)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, null)
        argumentCaptor<Throwable> {
            verify(mockApmNetworkInstrumentation).onResponseFailed(any(), capture())
            assertThat(firstValue).isInstanceOf(IOException::class.java)
            assertThat(firstValue.message).isEqualTo("Response failed")
        }
    }

    @Test
    fun `M only delegate onFailed W onFailed() {traceState null}`() {
        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
        verifyNoInteractions(mockApmNetworkInstrumentation)
    }

    @Test
    fun `M only delegate onFailed W onFailed() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = DatadogRequestCallback(mockDelegate, null)

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
    }

    // endregion

    // region onCanceled

    @Test
    fun `M delegate onCanceled and call apmNetworkInstrumentation W onCanceled() {traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(mockRequestInfo)

        // When
        testedCallback.onCanceled(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onCanceled(mockUrlRequest, mockUrlResponseInfo)
        argumentCaptor<Throwable> {
            verify(mockApmNetworkInstrumentation).onResponseFailed(any(), capture())
            assertThat(firstValue).isInstanceOf(IOException::class.java)
            assertThat(firstValue.message).isEqualTo("Response cancelled")
        }
    }

    @Test
    fun `M only delegate onCanceled W onCanceled() {traceState null}`() {
        // When
        testedCallback.onCanceled(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onCanceled(mockUrlRequest, mockUrlResponseInfo)
        verifyNoInteractions(mockApmNetworkInstrumentation)
    }

    // endregion

    // region onRequestStarted

    @Test
    fun `M return traceState W onRequestStarted()`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(mockRequestInfo)) doReturn mockTraceState

        // When
        val result = testedCallback.onRequestStarted(mockRequestInfo)

        // Then
        assertThat(result).isSameAs(mockTraceState)
    }

    @Test
    fun `M return null W onRequestStarted() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = DatadogRequestCallback(mockDelegate, null)

        // When
        val result = testedCallback.onRequestStarted(mockRequestInfo)

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
