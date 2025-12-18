/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.UploadDataProvider
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
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetRequestInfoTest {

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    lateinit var fakeMethod: String
    lateinit var fakeHeaders: Map<String, List<String>>

    @Mock
    lateinit var mockUploadDataProvider: UploadDataProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMethod = forge.anElementFrom(HttpSpec.Method.values())
        fakeHeaders = forge.exhaustiveAttributes().mapValues { listOf(it.value.toString()) }
    }

    @Test
    fun `M return url W url property`() {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M return method W method property`() {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.method

        // Then
        assertThat(result).isEqualTo(fakeMethod)
    }

    @Test
    fun `M return headers W headers property`() {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
    }

    @Test
    fun `M return annotations W annotations property`(
        forge: Forge
    ) {
        // Given
        val fakeAnnotations = listOf(
            forge.anAlphabeticalString(),
            forge.anInt(),
            forge.aBool(),
            forge.aChar(),
            forge.anInt().toByte(),
            forge.aLong(),
            forge.aFloat(),
            forge.aDouble()
        )
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = fakeAnnotations
        )

        // Then
        assertThat(requestInfo.tag(String::class.java)).isEqualTo(fakeAnnotations[0])
        assertThat(requestInfo.tag(Int::class.java)).isEqualTo(fakeAnnotations[1])
        assertThat(requestInfo.tag(Boolean::class.java)).isEqualTo(fakeAnnotations[2])
        assertThat(requestInfo.tag(Char::class.java)).isEqualTo(fakeAnnotations[3])
        assertThat(requestInfo.tag(Byte::class.java)).isEqualTo(fakeAnnotations[4])
        assertThat(requestInfo.tag(Long::class.java)).isEqualTo(fakeAnnotations[5])
        assertThat(requestInfo.tag(Float::class.java)).isEqualTo(fakeAnnotations[6])
        assertThat(requestInfo.tag(Double::class.java)).isEqualTo(fakeAnnotations[7])
    }

    @Test
    fun `M return content type W contentType property { content type header present }`(
        @StringForgery fakeContentType: String
    ) {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders + mapOf(HttpSpec.Headers.CONTENT_TYPE to listOf(fakeContentType)),
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.contentType

        // Then
        assertThat(result).isEqualTo(fakeContentType)
    }

    @Test
    fun `M return null W contentType property { no content type header }`() {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val actual = requestInfo.contentType

        // Then
        assertThat(actual).isNull()
    }

    @Test
    fun `M return null W tag() { annotation type does not match }`(forge: Forge) {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = listOf(forge.aString(), forge.anInt(), forge.aBool())
        )

        // When
        val result = requestInfo.tag(Double::class.java)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W tag() { no annotations }`() {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.tag(String::class.java)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return content length W contentLength() { upload data provider has length }`(
        @LongForgery(min = 1) fakeLength: Long
    ) {
        // Given
        whenever(mockUploadDataProvider.length).thenReturn(fakeLength)
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = mockUploadDataProvider,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isEqualTo(fakeLength)
    }

    @Test
    fun `M return content length from headers W contentLength() { upload data provider has length }`(
        @LongForgery(min = 1) fakeLength: Long
    ) {
        // Given
        whenever(mockUploadDataProvider.length).thenReturn(fakeLength)
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders + mapOf(HttpSpec.Headers.CONTENT_LENGTH to listOf(fakeLength.toString())),
            uploadDataProvider = mockUploadDataProvider,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isEqualTo(fakeLength)
    }

    @Test
    fun `M return null W contentLength() { upload data provider length is unknown }`() {
        // Given
        whenever(mockUploadDataProvider.length).thenReturn(-1L)
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = emptyMap(),
            uploadDataProvider = mockUploadDataProvider,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W contentLength() { no upload data provider }`() {
        // Given
        val requestInfo = CronetHttpRequestInfo(
            url = fakeUrl,
            method = fakeMethod,
            headers = fakeHeaders,
            uploadDataProvider = null,
            annotations = emptyList()
        )

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isNull()
    }
}
