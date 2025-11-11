/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.tests.elmyr.exhaustiveAttributes
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class OkHttpRequestInfoTest {

    @Test
    fun `M delegate W url property`(@StringForgery fakeUrl: String) {
        // Given
        val mockHttpUrl = mock<okhttp3.HttpUrl> { on { toString() } doReturn fakeUrl }
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
}
