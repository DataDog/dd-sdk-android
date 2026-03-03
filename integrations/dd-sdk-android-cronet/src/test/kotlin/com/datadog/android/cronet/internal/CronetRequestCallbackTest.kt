/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.tests.elmyr.anUrlString
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetRequestCallbackTest {

    private lateinit var testedCallback: CronetRequestCallback

    @Mock
    lateinit var mockDelegate: UrlRequest.Callback

    @Mock
    lateinit var mockApmNetworkInstrumentation: ApmNetworkInstrumentation

    @Mock
    lateinit var mockUrlRequest: UrlRequest

    @Mock
    lateinit var mockUrlResponseInfo: UrlResponseInfo

    @Mock
    lateinit var mockByteBuffer: ByteBuffer

    @Mock
    lateinit var mockCronetException: CronetException

    @Mock
    lateinit var mockTraceState: RequestTracingState

    @Mock
    lateinit var mockDistributedTracingInstrumentation: ApmNetworkInstrumentation

    @Mock
    lateinit var mockRumNetworkInstrumentation: RumNetworkInstrumentation

    @Mock
    lateinit var mockRequestBuilder: HttpRequestInfoBuilder

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockContextCallback: CronetRequestCallback

    @Mock
    lateinit var mockContextExecutor: Executor

    @Mock(extraInterfaces = [MutableHttpRequestInfo::class])
    lateinit var mockMutableRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockRedirectRequestInfoBuilder: HttpRequestInfoBuilder

    @Mock
    lateinit var mockRedirectRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockRedirectTraceState: RequestTracingState

    lateinit var fakeRequestInfo: CronetHttpRequestInfo

    @StringForgery
    lateinit var fakeUrl: String

    @StringForgery
    lateinit var fakeNewLocationUrl: String

    @IntForgery(min = 100, max = 599)
    var fakeStatusCode: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockRedirectRequestInfoBuilder = mock {
            on { setUrl(any()) } doReturn it
            on { setMethod(any(), anyOrNull()) } doReturn it
            on { build() } doReturn mockRedirectRequestInfo
        }
        mockTraceState = mock {
            on { tracedRequestInfoBuilder } doReturn mockRequestBuilder
            on { createModifiedRequestInfo() } doReturn mockMutableRequestInfo
        }
        mockApmNetworkInstrumentation = mock {
            on { networkTracingScope } doReturn ApmNetworkTracingScope.ALL
            on { onRequest(mockRedirectRequestInfo) } doReturn mockRedirectTraceState
        }

        mockUrlResponseInfo = mock {
            on { url } doReturn fakeUrl
            on { httpStatusCode } doReturn fakeStatusCode
            on { allHeaders } doReturn emptyMap()
        }

        whenever(
            (mockMutableRequestInfo as MutableHttpRequestInfo).newBuilder()
        ) doReturn mockRedirectRequestInfoBuilder

        val requestContext = CronetRequestContext(
            url = forge.anUrlString(),
            engine = mockEngine,
            requestCallback = mockContextCallback,
            executor = mockContextExecutor
        )

        fakeRequestInfo = requestContext.asCronetRequestInfo()
        testedCallback = CronetRequestCallback(
            mockDelegate,
            mockApmNetworkInstrumentation,
            null,
            mockDistributedTracingInstrumentation
        )
    }

    // region onRedirectReceived

    @Test
    fun `M delegate onRedirectReceived with wrapped request W onRedirectReceived() {ALL scope}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        argumentCaptor<UrlRequest> {
            verify(mockDelegate).onRedirectReceived(capture(), eq(mockUrlResponseInfo), eq(fakeNewLocationUrl))
            assertThat(firstValue).isInstanceOf(CronetRedirectTracingRequestWrapper::class.java)
            assertThat((firstValue as CronetRedirectTracingRequestWrapper).delegate).isSameAs(mockUrlRequest)
        }
    }

    @Test
    fun `M finish current span W onRedirectReceived() {ALL scope, traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        argumentCaptor<CronetHttpResponseInfo> {
            verify(mockApmNetworkInstrumentation).onResponseSucceeded(eq(mockTraceState), capture())
            assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
    }

    @Test
    fun `M delegate without tracing W onRedirectReceived() {APPLICATION_LEVEL scope}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.networkTracingScope) doReturn
            ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
        verify(mockApmNetworkInstrumentation, never()).onResponseFailed(any(), any())
    }

    @Test
    fun `M not call onResponseSucceeded W onRedirectReceived() {traceState null, ALL scope}`() {
        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(
            isA<CronetRedirectTracingRequestWrapper>(),
            eq(mockUrlResponseInfo),
            eq(fakeNewLocationUrl)
        )
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
    }

    @Test
    fun `M create redirect span W onRedirectReceived() {ALL, MutableHttpRequestInfo, follows redirect}`(
        @StringForgery fakeMethod: String
    ) {
        // Given
        whenever(mockMutableRequestInfo.method) doReturn fakeMethod
        whenever(mockApmNetworkInstrumentation.onRequest(fakeRequestInfo)) doReturn mockTraceState
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockApmNetworkInstrumentation).onResponseSucceeded(eq(mockTraceState), isA<CronetHttpResponseInfo>())
        verify(mockApmNetworkInstrumentation).removeTracingHeaders(mockRedirectRequestInfoBuilder)
        verify(mockRedirectRequestInfoBuilder).setUrl(fakeNewLocationUrl)
        verify(mockApmNetworkInstrumentation).onRequest(mockRedirectRequestInfo)
    }

    @Test
    fun `M rewrite method to GET W onRedirectReceived() {301-302-303, non-safe method}`(
        forge: Forge
    ) {
        // Given
        // 301/302 only rewrite POST→GET; 303 rewrites any non-HEAD→GET
        val (fakeStatusCode, fakeMethod) = forge.anElementFrom(
            HttpSpec.StatusCode.MOVED_PERMANENTLY to HttpSpec.Method.POST,
            HttpSpec.StatusCode.FOUND to HttpSpec.Method.POST,
            HttpSpec.StatusCode.SEE_OTHER to HttpSpec.Method.PUT,
            HttpSpec.StatusCode.SEE_OTHER to HttpSpec.Method.POST,
            HttpSpec.StatusCode.SEE_OTHER to HttpSpec.Method.PATCH
        )

        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockMutableRequestInfo.method) doReturn fakeMethod
        whenever(mockApmNetworkInstrumentation.onRequest(fakeRequestInfo)) doReturn mockTraceState
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockRedirectRequestInfoBuilder).setMethod(HttpSpec.Method.GET, null)
    }

    @Test
    fun `M preserve method W onRedirectReceived() {307-308}`(
        forge: Forge
    ) {
        // Given
        val fakeStatusCode = forge.anElementFrom(
            HttpSpec.StatusCode.TEMPORARY_REDIRECT,
            HttpSpec.StatusCode.PERMANENT_REDIRECT
        )

        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockMutableRequestInfo.method) doReturn HttpSpec.Method.POST
        whenever(mockApmNetworkInstrumentation.onRequest(fakeRequestInfo)) doReturn mockTraceState
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockRedirectRequestInfoBuilder).setMethod(HttpSpec.Method.POST, null)
    }

    @Test
    fun `M preserve method W onRedirectReceived() {301-302-303, safe method}`(
        forge: Forge
    ) {
        // Given
        val method = HttpSpec.Method.GET
        val fakeStatusCode = forge.anElementFrom(
            HttpSpec.StatusCode.MOVED_PERMANENTLY,
            HttpSpec.StatusCode.FOUND,
            HttpSpec.StatusCode.SEE_OTHER
        )

        whenever(mockUrlResponseInfo.httpStatusCode) doReturn fakeStatusCode
        whenever(mockMutableRequestInfo.method) doReturn method
        whenever(mockApmNetworkInstrumentation.onRequest(fakeRequestInfo)) doReturn mockTraceState
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockRedirectRequestInfoBuilder).setMethod(method, null)
    }

    @Test
    fun `M not create redirect span W onRedirectReceived() {ALL, MutableHttpRequestInfo, no followRedirect}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(fakeRequestInfo)) doReturn mockTraceState

        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockApmNetworkInstrumentation).onResponseSucceeded(eq(mockTraceState), isA<CronetHttpResponseInfo>())
        verify(mockApmNetworkInstrumentation, never()).removeTracingHeaders(any())
    }

    @Test
    fun `M not create redirect span W onRedirectReceived() {ALL, non-MutableHttpRequestInfo}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(fakeRequestInfo)) doReturn mockTraceState
        whenever(mockTraceState.createModifiedRequestInfo()) doReturn mock<HttpRequestInfo>()
        whenever(mockDelegate.onRedirectReceived(any(), any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<UrlRequest>(0)
            request.followRedirect()
        }

        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockApmNetworkInstrumentation).onResponseSucceeded(eq(mockTraceState), isA<CronetHttpResponseInfo>())
        verify(mockApmNetworkInstrumentation, never()).removeTracingHeaders(any())
    }

    @Test
    fun `M only delegate onRedirectReceived W onRedirectReceived() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = CronetRequestCallback(mockDelegate, null, null, null)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        verify(mockDelegate).onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)
    }

    @Test
    fun `M call distributedTracingInstrumentation W onRedirectReceived() {distributedTracingState set, ALL scope}`() {
        // Given
        val mockDistributedTracingState: RequestTracingState = mock()
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onRedirectReceived(mockUrlRequest, mockUrlResponseInfo, fakeNewLocationUrl)

        // Then
        argumentCaptor<CronetHttpResponseInfo> {
            verify(
                mockDistributedTracingInstrumentation
            ).onResponseSucceeded(eq(mockDistributedTracingState), capture())
            assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
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
    fun `M delegate onSucceeded and call apmNetworkInstrumentation W onSucceeded() {traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
        argumentCaptor<CronetHttpResponseInfo> {
            verify(mockApmNetworkInstrumentation).onResponseSucceeded(eq(mockTraceState), capture())
            assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
    }

    @Test
    fun `M only delegate onSucceeded W onSucceeded() {traceState null}`() {
        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
    }

    @Test
    fun `M only delegate onSucceeded W onSucceeded() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = CronetRequestCallback(mockDelegate, null, null, null)
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, mockUrlResponseInfo)
    }

    @Test
    fun `M call distributedTracingInstrumentation onResponseSucceeded W onSucceeded() {distributedTracingState set}`() {
        // Given
        val mockDistributedTracingState: RequestTracingState = mock()
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onSucceeded(mockUrlRequest, mockUrlResponseInfo)

        // Then
        argumentCaptor<CronetHttpResponseInfo> {
            verify(
                mockDistributedTracingInstrumentation
            ).onResponseSucceeded(eq(mockDistributedTracingState), capture())
            assertThat(firstValue.statusCode).isEqualTo(fakeStatusCode)
        }
    }

    @Test
    fun `M not call onResponseSucceeded W onSucceeded() {responseInfo null, traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onSucceeded(mockUrlRequest, null)

        // Then
        verify(mockDelegate).onSucceeded(mockUrlRequest, null)
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
    }

    // endregion

    // region onFailed

    @Test
    fun `M delegate onFailed and call apmNetworkInstrumentation W onFailed() {traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(fakeRequestInfo)

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
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, null)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, null)
        argumentCaptor<Throwable> {
            verify(mockApmNetworkInstrumentation).onResponseFailed(eq(mockTraceState), capture())
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
    }

    @Test
    fun `M only delegate onFailed W onFailed() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = CronetRequestCallback(mockDelegate, null, null, null)
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDelegate).onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)
    }

    @Test
    fun `M call distributedTracingInstrumentation onResponseFailed W onFailed() {distributedTracingState set}`() {
        // Given
        val mockDistributedTracingState: RequestTracingState = mock()
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onFailed(mockUrlRequest, mockUrlResponseInfo, mockCronetException)

        // Then
        verify(mockDistributedTracingInstrumentation).onResponseFailed(mockDistributedTracingState, mockCronetException)
    }

    // endregion

    // region onCanceled

    @Test
    fun `M delegate onCanceled and call apmNetworkInstrumentation W onCanceled() {traceState set}`() {
        // Given
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockTraceState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onCanceled(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onCanceled(mockUrlRequest, mockUrlResponseInfo)
        argumentCaptor<Throwable> {
            verify(mockApmNetworkInstrumentation).onResponseFailed(eq(mockTraceState), capture())
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
        verify(mockApmNetworkInstrumentation, never()).onResponseFailed(any(), any())
        verify(mockApmNetworkInstrumentation, never()).onResponseSucceeded(any(), any())
    }

    @Test
    fun `M only delegate onCanceled W onCanceled() {apmNetworkInstrumentation null}`() {
        // Given
        testedCallback = CronetRequestCallback(mockDelegate, null, null, null)
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onCanceled(mockUrlRequest, mockUrlResponseInfo)

        // Then
        verify(mockDelegate).onCanceled(mockUrlRequest, mockUrlResponseInfo)
    }

    @Test
    fun `M call distributedTracingInstrumentation onResponseFailed W onCanceled() {distributedTracingState set}`() {
        // Given
        val mockDistributedTracingState: RequestTracingState = mock()
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState
        testedCallback.onRequestStarted(fakeRequestInfo)

        // When
        testedCallback.onCanceled(mockUrlRequest, mockUrlResponseInfo)

        // Then
        argumentCaptor<Throwable> {
            verify(mockDistributedTracingInstrumentation).onResponseFailed(eq(mockDistributedTracingState), capture())
            assertThat(firstValue).isInstanceOf(IOException::class.java)
            assertThat(firstValue.message).isEqualTo("Response cancelled")
        }
    }

    // endregion

    // region onRequestStarted

    @Test
    fun `M return distributedTracingState W onRequestStarted()`() {
        // Given
        val mockDistributedTracingState: RequestTracingState = mock()
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState

        // When
        val result = testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        assertThat(result).isSameAs(mockDistributedTracingState)
    }

    @Test
    fun `M return default state W onRequestStarted() {distributedTracingInstrumentation null}`() {
        // Given
        testedCallback = CronetRequestCallback(mockDelegate, mockApmNetworkInstrumentation, null, null)

        // When
        val result = testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        assertThat(result.isSampled).isFalse()
        assertThat(result.span).isNull()
    }

    @Test
    fun `M return default state W onRequestStarted() {both instrumentations null}`() {
        // Given
        testedCallback = CronetRequestCallback(mockDelegate, null, null, null)

        // When
        val result = testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        assertThat(result.isSampled).isFalse()
        assertThat(result.span).isNull()
    }

    @Test
    fun `M call apmNetworkInstrumentation with initialRequestInfo W onRequestStarted() {no distributedTracingState}`() {
        // When
        testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        verify(mockApmNetworkInstrumentation).onRequest(fakeRequestInfo)
    }

    @Test
    fun `M call apmNetworkInstrumentation with modifiedRequestInfo W onRequestStarted() {tracingState set}`() {
        // Given
        val mockModifiedRequestInfo: HttpRequestInfo = mock()
        val mockDistributedTracingState: RequestTracingState = mock()
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState
        whenever(mockDistributedTracingState.createModifiedRequestInfo()) doReturn mockModifiedRequestInfo

        // When
        testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        verify(mockApmNetworkInstrumentation).onRequest(mockModifiedRequestInfo)
    }

    @Test
    fun `M call startResource and sendWaitForResourceTimingEvent W onRequestStarted() {no tracingInstrumentation}`() {
        // Given
        testedCallback = CronetRequestCallback(
            mockDelegate,
            mockApmNetworkInstrumentation,
            mockRumNetworkInstrumentation,
            null
        )

        // When
        testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        verify(mockRumNetworkInstrumentation).startResource(fakeRequestInfo)
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(fakeRequestInfo)
    }

    @Test
    fun `M call startResource and sendWaitForResourceTimingEvent W onRequestStarted() {tracingInstrumentation set}`() {
        // Given
        val mockModifiedRequestInfo: HttpRequestInfo = mock()
        val mockDistributedTracingState: RequestTracingState = mock()
        testedCallback = CronetRequestCallback(
            mockDelegate,
            mockApmNetworkInstrumentation,
            mockRumNetworkInstrumentation,
            mockDistributedTracingInstrumentation
        )
        whenever(mockDistributedTracingInstrumentation.onRequest(fakeRequestInfo)) doReturn mockDistributedTracingState
        whenever(mockDistributedTracingState.createModifiedRequestInfo()) doReturn mockModifiedRequestInfo

        // When
        testedCallback.onRequestStarted(fakeRequestInfo)

        // Then
        verify(mockRumNetworkInstrumentation).startResource(mockModifiedRequestInfo)
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockModifiedRequestInfo)
    }

    // endregion
}
