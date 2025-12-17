/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.okhttp.internal.OkHttpHttpResponseInfo.Companion.ERROR_PEEK_BODY
import com.datadog.android.okhttp.utils.verifyLog
import com.datadog.android.tests.elmyr.exhaustiveAttributes
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
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
        val result = OkHttpHttpResponseInfo(mockResponse, mockInternalLogger).url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M delegate W statusCode property`(@IntForgery(min = 100, max = 599) fakeStatusCode: Int) {
        // Given
        val mockResponse = mock<Response> { on { code } doReturn fakeStatusCode }

        // When
        val result = OkHttpHttpResponseInfo(mockResponse, mockInternalLogger).statusCode

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
        val result = OkHttpHttpResponseInfo(mockResponse, mockInternalLogger).headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
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
        val result = OkHttpHttpResponseInfo(mockResponse, mockInternalLogger).contentType

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
        val result = OkHttpHttpResponseInfo(response, mockInternalLogger).contentLength

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
        val result = OkHttpHttpResponseInfo(response, mockInternalLogger).contentLength

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
        OkHttpHttpResponseInfo(response, mockInternalLogger).contentLength

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
        OkHttpHttpResponseInfo(response, mockInternalLogger).contentLength

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
        OkHttpHttpResponseInfo(response, mockInternalLogger).contentLength

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            ERROR_PEEK_BODY,
            throwable
        )
    }
}
