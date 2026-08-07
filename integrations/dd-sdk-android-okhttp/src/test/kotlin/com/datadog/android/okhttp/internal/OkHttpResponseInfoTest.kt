/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpBodySnapshot
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.okhttp.internal.OkHttpResponseInfo.Companion.ERROR_PEEK_BODY
import com.datadog.android.tests.elmyr.exhaustiveAttributes
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class OkHttpResponseInfoTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M delegate W url property`(@StringForgery fakeUrl: String) {
        // Given
        val mockHttpUrl = mock<HttpUrl> { on { toString() } doReturn fakeUrl }
        val mockRequest = mock<Request> { on { url } doReturn mockHttpUrl }
        val mockResponse = mock<Response> { on { request } doReturn mockRequest }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger).url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M delegate W statusCode property`(@IntForgery(min = 100, max = 599) fakeStatusCode: Int) {
        // Given
        val mockResponse = mock<Response> { on { code } doReturn fakeStatusCode }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger).statusCode

        // Then
        assertThat(result).isEqualTo(fakeStatusCode)
    }

    @Test
    fun `M delegate W headers property`(forge: Forge) {
        // Given
        val fakeHeaders = forge.exhaustiveAttributes().mapValues { listOf(it.value.toString()) }
        val mockHeaders = mock<Headers> { on { toMultimap() } doReturn fakeHeaders }
        val mockResponse = mock<Response> { on { headers } doReturn mockHeaders }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger).headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
    }

    @Test
    fun `M delegate W request property`() {
        // Given
        val mockRequest = mock<Request>()
        val mockResponse = mock<Response> { on { request } doReturn mockRequest }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger).request

        // Then
        assertThat(result).isInstanceOf(OkHttpRequestInfo::class.java)
        assertThat(result.toOkHttpRequest()).isSameAs(mockRequest)
    }

    @Test
    fun `M delegate W contentType property { content type header present }`(
        @StringForgery fakeContentType: String,
        @StringForgery fakeContentSubType: String
    ) {
        // Given
        val contentType = "$fakeContentType/$fakeContentSubType".lowercase()
        val mediaType = contentType.toMediaType()
        val responseBody = mock<ResponseBody> { on { contentType() } doReturn mediaType }
        val mockResponse = mock<Response> { on { body } doReturn responseBody }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger).contentType

        // Then
        assertThat(result).isEqualTo(contentType)
    }

    @Test
    fun `M delegate W computeContentLength() { body present }`(
        @LongForgery(min = 1, max = 1000) fakeContentLength: Long
    ) {
        // Given
        val mockResponseBody = mock<ResponseBody> { on { contentLength() } doReturn fakeContentLength }
        val response = mock<Response> { on { body } doReturn mockResponseBody }

        // When
        val result = OkHttpResponseInfo(response, mockInternalLogger).contentLength

        // Then
        assertThat(result).isEqualTo(fakeContentLength)
    }

    @Test
    fun `M delegate W computeContentLength() { peak body present }`(
        @LongForgery(min = 1, max = 1000) fakeContentLength: Long
    ) {
        // Given
        val mockResponseBody = mock<ResponseBody> { on { contentLength() } doReturn fakeContentLength }
        val response = mock<Response> { on { peekBody(any()) } doReturn mockResponseBody }

        // When
        val result = OkHttpResponseInfo(response, mockInternalLogger).contentLength

        // Then
        assertThat(result).isEqualTo(fakeContentLength)
    }

    @Test
    fun `M log error W computeContentLength() { peak body throws IOException }`() {
        // Given
        val throwable = IOException()
        val response = mock<Response> {
            on { peekBody(any()) } doThrow throwable
        }

        // When
        OkHttpResponseInfo(response, mockInternalLogger).contentLength

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ERROR_PEEK_BODY,
            throwable
        )
    }

    @Test
    fun `M log error W computeContentLength() { peak body throws IllegalStateException }`() {
        // Given
        val throwable = IllegalStateException()
        val response = mock<Response> {
            on { peekBody(any()) } doThrow throwable
        }

        // When
        OkHttpResponseInfo(response, mockInternalLogger).contentLength

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ERROR_PEEK_BODY,
            throwable
        )
    }

    @Test
    fun `M log error W computeContentLength() { peak body throws IllegalArgumentException }`() {
        // Given
        val throwable = IllegalArgumentException()
        val response = mock<Response> {
            on { peekBody(any()) } doThrow throwable
        }

        // When
        OkHttpResponseInfo(response, mockInternalLogger).contentLength

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            ERROR_PEEK_BODY,
            throwable
        )
    }

    // region peekBody

    @Test
    fun `M return the payload W peekBody() { body smaller than the limit }`(
        @StringForgery(size = 64) fakePayload: String
    ) {
        // Given
        val fakeBytes = fakePayload.toByteArray()
        val fakeResponse = fakeResponse(fakeBytes, CONTENT_TYPE_JSON)

        // When
        val result = OkHttpResponseInfo(fakeResponse, mockInternalLogger).peekBody(fakeBytes.size + 1L)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEqualTo(fakeBytes)
        assertThat(result.isTruncated).isFalse()
        assertThat(result.contentType).isEqualTo(CONTENT_TYPE_JSON)
        assertThat(result.string()).isEqualTo(fakePayload)
    }

    @Test
    fun `M return the whole payload W peekBody() { body exactly at the limit }`(
        @StringForgery(size = 64) fakePayload: String
    ) {
        // Given
        val fakeBytes = fakePayload.toByteArray()
        val fakeResponse = fakeResponse(fakeBytes)

        // When
        val result = OkHttpResponseInfo(fakeResponse, mockInternalLogger).peekBody(fakeBytes.size.toLong())

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEqualTo(fakeBytes)
        assertThat(result.isTruncated).isFalse()
    }

    @Test
    fun `M truncate the payload W peekBody() { body larger than the limit }`(
        @StringForgery(size = 512) fakePayload: String
    ) {
        // Given
        val fakeBytes = fakePayload.toByteArray()
        val fakeLimit = 32L
        val fakeResponse = fakeResponse(fakeBytes)

        // When
        val result = OkHttpResponseInfo(fakeResponse, mockInternalLogger).peekBody(fakeLimit)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEqualTo(fakeBytes.copyOf(fakeLimit.toInt()))
        assertThat(result.isTruncated).isTrue()
    }

    @Test
    fun `M cap the payload W peekBody() { requested limit exceeds hard maximum }`() {
        // Given
        val hardMaximum = HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES.toInt()
        val fakeResponse = fakeResponse(ByteArray(hardMaximum + 1))

        // When
        val result = OkHttpResponseInfo(fakeResponse, mockInternalLogger).peekBody(Long.MAX_VALUE)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).hasSize(hardMaximum)
        assertThat(result.isTruncated).isTrue()
    }

    @Test
    fun `M leave the body readable W peekBody()`(@StringForgery(size = 64) fakePayload: String) {
        // Given
        val fakeBytes = fakePayload.toByteArray()
        val fakeResponse = fakeResponse(fakeBytes)

        // When
        OkHttpResponseInfo(fakeResponse, mockInternalLogger).peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(fakeResponse.body?.string()).isEqualTo(fakePayload)
    }

    @Test
    fun `M return null W peekBody() { no body }`() {
        // Given
        val mockResponse = mock<Response> { on { body } doReturn null }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W peekBody() { non positive limit }`(
        @LongForgery(min = Long.MIN_VALUE, max = 1L) fakeLimit: Long,
        @StringForgery(size = 64) fakePayload: String
    ) {
        // Given
        val fakeResponse = fakeResponse(fakePayload.toByteArray())

        // When
        val result = OkHttpResponseInfo(fakeResponse, mockInternalLogger).peekBody(fakeLimit)

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "text/event-stream",
            "application/grpc",
            "application/grpc+proto",
            "application/grpc+json"
        ]
    )
    fun `M return null W peekBody() { streaming content type }`(
        fakeContentType: String
    ) {
        // Given
        val mockResponseBody = mock<ResponseBody> {
            on { contentType() } doReturn fakeContentType.toMediaType()
        }
        val mockResponse = mock<Response> {
            on { body } doReturn mockResponseBody
            on { header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null) } doReturn null
        }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
        verify(mockResponse, never()).peekBody(any())
    }

    @Test
    fun `M return null W peekBody() { websocket response }`(
        @StringForgery(type = StringForgeryType.ALPHABETICAL) fakeAcceptKey: String
    ) {
        // Given
        val mockResponseBody = mock<ResponseBody>()
        val mockResponse = mock<Response> {
            on { body } doReturn mockResponseBody
            on { header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null) } doReturn fakeAcceptKey
        }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
        verify(mockResponse, never()).peekBody(any())
    }

    @Test
    fun `M log error and return null W peekBody() { peek throws }`(
        @StringForgery(size = 64) fakePayload: String
    ) {
        // Given
        val fakeThrowable = IOException("Broken body")
        val mockResponse = mock<Response> {
            on { body } doReturn fakePayload.toResponseBody()
            on { header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null) } doReturn null
            on { peekBody(any()) } doThrow fakeThrowable
        }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ERROR_PEEK_BODY,
            fakeThrowable
        )
    }

    @Test
    fun `M log error and return null W peekBody() { custom body fails unexpectedly }`(
        @StringForgery(size = 64) fakePayload: String
    ) {
        // Given
        val fakeThrowable = IllegalArgumentException("Unexpected")
        val mockResponse = mock<Response> {
            on { body } doReturn fakePayload.toResponseBody()
            on { header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null) } doReturn null
            on { peekBody(any()) } doThrow fakeThrowable
        }

        // When
        val result = OkHttpResponseInfo(mockResponse, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ERROR_PEEK_BODY,
            fakeThrowable
        )
    }

    @Test
    fun `M return an empty snapshot W peekBody() { empty body }`() {
        // Given
        val fakeResponse = fakeResponse(ByteArray(0))

        // When
        val result = OkHttpResponseInfo(fakeResponse, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEmpty()
        assertThat(result.isTruncated).isFalse()
    }

    // endregion

    private fun fakeResponse(payload: ByteArray, contentType: String? = null): Response =
        Response.Builder()
            .request(Request.Builder().url(FAKE_URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(HttpSpec.StatusCode.OK)
            .message("OK")
            .body(payload.toResponseBody(contentType?.toMediaType()))
            .build()

    companion object {
        private const val FAKE_URL = "https://example.com/resource"
        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
    }
}
