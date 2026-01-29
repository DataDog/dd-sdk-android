/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.cronet.DatadogCronetEngine
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
import java.util.concurrent.Executor

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
    lateinit var fakeHeaders: MutableMap<String, List<String>>

    @Mock
    lateinit var mockExecutor: Executor

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: DatadogRequestCallback

    @Mock
    lateinit var mockUploadDataProvider: UploadDataProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMethod = forge.anElementFrom(HttpSpec.Method.values())
        fakeHeaders = forge.exhaustiveAttributes().mapValues { listOf(it.value.toString()) }.toMutableMap()
    }

    @Test
    fun `M return url W url property`() {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.url

        // Then
        assertThat(result).isEqualTo(fakeUrl)
    }

    @Test
    fun `M return method W method property`() {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.method

        // Then
        assertThat(result).isEqualTo(fakeMethod)
    }

    @Test
    fun `M return headers W headers property`() {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        fakeHeaders.forEach { (key, values) ->
            values.forEach { value ->
                requestContext.addHeader(key, value)
            }
        }
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.headers

        // Then
        assertThat(result).isEqualTo(fakeHeaders)
    }

    @Test
    fun `M return annotations W tag() property`(
        forge: Forge
    ) {
        // Given
        val fakeString = forge.anAlphabeticalString()
        val fakeInt = forge.anInt()
        val fakeBool = forge.aBool()
        val fakeChar = forge.aChar()
        val fakeByte = forge.anInt().toByte()
        val fakeLong = forge.aLong()
        val fakeFloat = forge.aFloat()
        val fakeDouble = forge.aDouble()

        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        requestContext.setTag(String::class.java, fakeString)
        requestContext.setTag(Int::class.java, fakeInt)
        requestContext.setTag(Boolean::class.java, fakeBool)
        requestContext.setTag(Char::class.java, fakeChar)
        requestContext.setTag(Byte::class.java, fakeByte)
        requestContext.setTag(Long::class.java, fakeLong)
        requestContext.setTag(Float::class.java, fakeFloat)
        requestContext.setTag(Double::class.java, fakeDouble)

        val requestInfo = CronetHttpRequestInfo(requestContext)

        // Then
        assertThat(requestInfo.tag(String::class.java)).isEqualTo(fakeString)
        assertThat(requestInfo.tag(Int::class.java)).isEqualTo(fakeInt)
        assertThat(requestInfo.tag(Boolean::class.java)).isEqualTo(fakeBool)
        assertThat(requestInfo.tag(Char::class.java)).isEqualTo(fakeChar)
        assertThat(requestInfo.tag(Byte::class.java)).isEqualTo(fakeByte)
        assertThat(requestInfo.tag(Long::class.java)).isEqualTo(fakeLong)
        assertThat(requestInfo.tag(Float::class.java)).isEqualTo(fakeFloat)
        assertThat(requestInfo.tag(Double::class.java)).isEqualTo(fakeDouble)
    }

    @Test
    fun `M return content type W contentType property { content type header present }`(
        @StringForgery fakeContentType: String
    ) {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        requestContext.addHeader(HttpSpec.Headers.CONTENT_TYPE, fakeContentType)
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.contentType

        // Then
        assertThat(result).isEqualTo(fakeContentType)
    }

    @Test
    fun `M return null W contentType property { no content type header }`() {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val actual = requestInfo.contentType

        // Then
        assertThat(actual).isNull()
    }

    @Test
    fun `M return null W tag() { annotation type does not match }`(forge: Forge) {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        requestContext.setTag(String::class.java, forge.aString())
        requestContext.setTag(Int::class.java, forge.anInt())
        requestContext.setTag(Boolean::class.java, forge.aBool())
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.tag(Double::class.java)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W tag() { no annotations }`() {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        val requestInfo = CronetHttpRequestInfo(requestContext)

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
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        requestContext.setUploadDataProvider(mockUploadDataProvider, mockExecutor)
        val requestInfo = CronetHttpRequestInfo(requestContext)

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
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        requestContext.addHeader(HttpSpec.Headers.CONTENT_LENGTH, fakeLength.toString())
        requestContext.setUploadDataProvider(mockUploadDataProvider, mockExecutor)
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isEqualTo(fakeLength)
    }

    @Test
    fun `M return null W contentLength() { upload data provider length is unknown }`() {
        // Given
        whenever(mockUploadDataProvider.length).thenReturn(-1L)
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        requestContext.setUploadDataProvider(mockUploadDataProvider, mockExecutor)
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W contentLength() { no upload data provider }`() {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(fakeMethod) }
        val requestInfo = CronetHttpRequestInfo(requestContext)

        // When
        val result = requestInfo.contentLength()

        // Then
        assertThat(result).isNull()
    }
}
