/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.UrlResponseInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CronetResponseInfoTest {

    @Mock
    lateinit var mockUrlResponseInfo: UrlResponseInfo

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    lateinit var fakeHeaders: Map<String, List<String>>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeHeaders = forge.exhaustiveAttributes().mapValues { listOf(it.value.toString()) }
    }

    @Test
    fun `M return url W url property`(
        @StringForgery fakeUrl: String
    ) {
        // Given
        whenever(mockUrlResponseInfo.url).thenReturn(fakeUrl)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M return status code W statusCode property`(
        @IntForgery(min = 100, max = 599) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockUrlResponseInfo.httpStatusCode).thenReturn(fakeStatusCode)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.statusCode

        // Then
        assertThat(result).isEqualTo(fakeStatusCode)
    }

    @Test
    fun `M return headers W headers property`() {
        // Given

        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
    }

    @Test
    fun `M return content type W contentType property { content type header present }`(
        @StringForgery fakeContentType: String
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(HttpSpec.Headers.CONTENT_TYPE to listOf(fakeContentType))
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentType

        // Then
        assertThat(result).isEqualTo(fakeContentType)
    }

    @Test
    fun `M return null W contentType property { no content type header }`() {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentType

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return content length from header W computeContentLength() { header present }`(
        @LongForgery(min = 1) fakeContentLength: Long
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(HttpSpec.Headers.CONTENT_LENGTH to listOf(fakeContentLength.toString()))
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(fakeContentLength)
    }

    @Test
    fun `M return received byte count W computeContentLength() { no header, positive byte count }`(
        @LongForgery(min = 1) fakeByteCount: Long
    ) {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(fakeByteCount)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(fakeByteCount)
    }

    @Test
    fun `M return zero W computeContentLength() { no header, byte count is zero }`() {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(0L)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `M return null W computeContentLength() { no header, negative byte count }`() {
        // Given
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(emptyMap())
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(-1L)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W computeContentLength() { header not a valid number }`(
        @StringForgery fakeInvalidContentLength: String
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(
            HttpSpec.Headers.CONTENT_LENGTH to listOf(fakeInvalidContentLength)
        )
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(100L)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(100L)
    }

    @Test
    fun `M prefer header over byte count W computeContentLength() { both available }`(
        @LongForgery(min = 1000) fakeHeaderLength: Long,
        @LongForgery(min = 1, max = 999) fakeByteCount: Long
    ) {
        // Given
        val fakeHeaders = fakeHeaders + mapOf(HttpSpec.Headers.CONTENT_LENGTH to listOf(fakeHeaderLength.toString()))
        whenever(mockUrlResponseInfo.allHeaders).thenReturn(fakeHeaders)
        whenever(mockUrlResponseInfo.receivedByteCount).thenReturn(fakeByteCount)
        val responseInfo = CronetHttpResponseInfo(mockUrlResponseInfo)

        // When
        val result = responseInfo.contentLength

        // Then
        assertThat(result).isEqualTo(fakeHeaderLength)
    }
}
