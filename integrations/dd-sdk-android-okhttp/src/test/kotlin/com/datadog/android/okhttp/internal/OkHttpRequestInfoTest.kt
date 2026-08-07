/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpBodySnapshot
import com.datadog.android.okhttp.internal.OkHttpRequestInfo.Companion.ERROR_PEEK_REQUEST_BODY
import com.datadog.android.tests.elmyr.exhaustiveAttributes
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class OkHttpRequestInfoTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M delegate W url property`(@StringForgery fakeUrl: String) {
        // Given
        val mockHttpUrl = mock<HttpUrl> { on { toString() } doReturn fakeUrl }
        val mockRequest = mock<Request> { on { url } doReturn mockHttpUrl }

        // When
        val result = OkHttpRequestInfo(mockRequest).url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M delegate W method property`(@StringForgery fakeMethod: String) {
        // Given
        val mockRequest = mock<Request> { on { method } doReturn fakeMethod }

        // When
        val result = OkHttpRequestInfo(mockRequest).method

        // Then
        assertThat(result).isEqualTo(fakeMethod)
    }

    @Test
    fun `M delegate W headers property`(forge: Forge) {
        // Given
        val fakeHeaders = forge.exhaustiveAttributes().mapValues { listOf(it.value.toString()) }
        val mockHeaders = mock<Headers> { on { toMultimap() } doReturn fakeHeaders }
        val mockRequest = mock<Request> { on { headers } doReturn mockHeaders }

        // When
        val result = OkHttpRequestInfo(mockRequest).headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
    }

    @Test
    fun `M delegate W contentType property { content type present }`(
        @StringForgery fakeContentType: String
    ) {
        // Given
        val mockMediaType = mock<MediaType> { on { toString() } doReturn fakeContentType }
        val mockRequestBody = mock<RequestBody> { on { contentType() } doReturn mockMediaType }
        val mockRequest = mock<Request> { on { body } doReturn mockRequestBody }

        // When
        val result = OkHttpRequestInfo(mockRequest).contentType

        // Then
        assertThat(result).isEqualTo(fakeContentType)
    }

    @Test
    fun `M delegate W tag`(@StringForgery fakeTag: String) {
        val mockRequest = mock<Request> {
            on { tag(String::class.java) } doReturn fakeTag
        }

        // When
        val tag = OkHttpRequestInfo(mockRequest).tag(String::class.java)

        // Then
        assertThat(tag).isEqualTo(fakeTag)
    }

    @Test
    fun `M delegate W contentLength() { request body has length }`(@LongForgery fakeContentLength: Long) {
        // Given
        val mockRequestBody = mock<RequestBody> { on { contentLength() } doReturn fakeContentLength }
        val mockRequest = mock<Request> { on { body } doReturn mockRequestBody }

        // When
        val result = OkHttpRequestInfo(mockRequest).contentLength()

        // Then
        assertThat(result).isEqualTo(fakeContentLength)
    }

    @Test
    fun `M preserve the logger W newBuilder()`() {
        // Given
        val fakeRequest = Request.Builder().url("https://example.com/").build()
        val testedInfo = OkHttpRequestInfo(fakeRequest, mockInternalLogger)

        // When
        val result = testedInfo.newBuilder().build() as OkHttpRequestInfo

        // Then
        assertThat(result.internalLogger).isSameAs(mockInternalLogger)
    }

    // region peekBody

    @Test
    fun `M return the payload W peekBody() { body smaller than the limit }`(
        @StringForgery(size = 64) fakePayload: String
    ) {
        // Given
        val fakeBytes = fakePayload.toByteArray()
        val mockRequest = mock<Request> {
            on { body } doReturn fakeBytes.toRequestBody(CONTENT_TYPE_JSON.toMediaType())
        }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger).peekBody(fakeBytes.size + 1L)

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
        val mockRequest = mock<Request> { on { body } doReturn fakeBytes.toRequestBody() }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger).peekBody(fakeBytes.size.toLong())

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
        val mockRequest = mock<Request> { on { body } doReturn fakeBytes.toRequestBody() }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger).peekBody(fakeLimit)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEqualTo(fakeBytes.copyOf(fakeLimit.toInt()))
        assertThat(result.isTruncated).isTrue()
    }

    @Test
    fun `M cap the payload W peekBody() { requested limit exceeds hard maximum }`() {
        // Given
        val hardMaximum = HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES.toInt()
        val fakeBytes = ByteArray(hardMaximum + 1)
        val mockRequest = mock<Request> { on { body } doReturn fakeBytes.toRequestBody() }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger).peekBody(Long.MAX_VALUE)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).hasSize(hardMaximum)
        assertThat(result.isTruncated).isTrue()
    }

    @Test
    fun `M not consume the body W peekBody()`(@StringForgery(size = 64) fakePayload: String) {
        // Given
        val fakeBytes = fakePayload.toByteArray()
        val fakeBody = fakeBytes.toRequestBody()
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }
        val testedRequestInfo = OkHttpRequestInfo(mockRequest, mockInternalLogger)

        // When
        testedRequestInfo.peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)
        val result = testedRequestInfo.peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEqualTo(fakeBytes)
    }

    @Test
    fun `M return null W peekBody() { no body }`() {
        // Given
        val mockRequest = mock<Request> { on { body } doReturn null }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W peekBody() { non positive limit }`(
        @LongForgery(min = Long.MIN_VALUE, max = 1L) fakeLimit: Long
    ) {
        // Given
        val mockBody = mock<RequestBody>()
        val mockRequest = mock<Request> { on { body } doReturn mockBody }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger).peekBody(fakeLimit)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockBody)
    }

    @Test
    fun `M return null W peekBody() { one shot body }`(@StringForgery(size = 64) fakePayload: String) {
        // Given
        val fakeBody = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                sink.write(fakePayload.toByteArray())
            }
        }
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W peekBody() { duplex body }`(@StringForgery(size = 64) fakePayload: String) {
        // Given
        val fakeBody = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun isDuplex(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                sink.write(fakePayload.toByteArray())
            }
        }
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M log error and return null W peekBody() { writeTo throws }`() {
        // Given
        val fakeThrowable = IOException("Broken body")
        val fakeBody = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun writeTo(sink: BufferedSink) {
                throw fakeThrowable
            }
        }
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            ERROR_PEEK_REQUEST_BODY,
            fakeThrowable
        )
    }

    @Test
    fun `M return an empty snapshot W peekBody() { empty body }`() {
        // Given
        val mockRequest = mock<Request> { on { body } doReturn ByteArray(0).toRequestBody() }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        checkNotNull(result)
        assertThat(result.bytes).isEmpty()
        assertThat(result.isTruncated).isFalse()
    }

    @Test
    fun `M return null W peekBody() { multipart body with a one shot part }`(
        @StringForgery(size = 32) fakePayload: String
    ) {
        // Given
        // MultipartBody does not override isOneShot(), so the part has to be inspected instead
        val fakeBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", fakePayload)
            .addFormDataPart("stream", "file.bin", oneShotBody(fakePayload))
            .build()
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return the payload W peekBody() { multipart body with rewindable parts }`(
        @StringForgery(size = 32) fakePayload: String
    ) {
        // Given
        val fakeBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", fakePayload)
            .build()
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger)
            .peekBody(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        // Then
        checkNotNull(result)
        assertThat(result.string()).contains(fakePayload)
    }

    @Test
    fun `M stop the body from producing the discarded part W peekBody() { body over the limit }`() {
        // Given
        val fakeChunk = ByteArray(CHUNK_SIZE)
        var writtenChunks = 0
        val fakeBody = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun writeTo(sink: BufferedSink) {
                repeat(CHUNK_COUNT) {
                    sink.write(fakeChunk)
                    sink.flush()
                    writtenChunks++
                }
            }
        }
        val mockRequest = mock<Request> { on { body } doReturn fakeBody }
        val fakeLimit = 2L * CHUNK_SIZE

        // When
        val result = OkHttpRequestInfo(mockRequest, mockInternalLogger).peekBody(fakeLimit)

        // Then
        checkNotNull(result)
        assertThat(result.isTruncated).isTrue()
        assertThat(result.bytes).hasSize(fakeLimit.toInt())
        // the point of the truncating sink: the rest of the payload is never serialized
        assertThat(writtenChunks).isLessThan(CHUNK_COUNT)
    }

    // endregion

    private fun oneShotBody(payload: String): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType? = null
        override fun isOneShot(): Boolean = true
        override fun writeTo(sink: BufferedSink) {
            sink.write(payload.toByteArray())
        }
    }

    companion object {
        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        private const val CHUNK_SIZE = 1024
        private const val CHUNK_COUNT = 64
    }
}
