/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.tests.elmyr.URL_FORGERY_PATTERN
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class ResponseExtTest {

    @Mock
    private lateinit var mockResponseBody: ResponseBody

    private lateinit var testedResponse: Response

    @BeforeEach
    fun `set up`(@StringForgery(regex = URL_FORGERY_PATTERN) fakeUrl: String) {
        testedResponse = Response.Builder()
            .request(Request.Builder().url(fakeUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(HttpSpec.StatusCode.OK)
            .message("OK")
            .body(mockResponseBody)
            .build()
    }

    @Test
    fun `M omit parameters W mimeType()`(
        @StringForgery(type = StringForgeryType.ALPHABETICAL) fakeSubtype: String
    ) {
        // Given
        val fakeMimeType = "application/${fakeSubtype.lowercase()}"
        val testedMediaType = "$fakeMimeType; charset=utf-8; boundary=example".toMediaType()

        // When
        val result = testedMediaType.mimeType()

        // Then
        assertThat(result).isEqualTo(fakeMimeType)
    }

    @ParameterizedTest
    @ValueSource(strings = ["text/event-stream", "application/grpc", "application/grpc+proto", "application/grpc+json"])
    fun `M identify streams without accessing body W isStreaming() { streaming MIME type }`(fakeMimeType: String) {
        // When
        val result = testedResponse.isStreaming(fakeMimeType)

        // Then
        assertThat(result).isTrue()
        verifyNoInteractions(mockResponseBody)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["application/json", "text/plain", "image/png", "application/octet-stream"])
    fun `M allow finite payloads without accessing body W isStreaming() { non streaming MIME type }`(
        fakeMimeType: String?
    ) {
        // When
        val result = testedResponse.isStreaming(fakeMimeType)

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(mockResponseBody)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["application/json"])
    fun `M identify websocket without accessing body W isStreaming() { accept header present }`(
        fakeMimeType: String?,
        @StringForgery(type = StringForgeryType.ALPHABETICAL) fakeAcceptKey: String
    ) {
        // Given
        testedResponse = testedResponse.newBuilder()
            .header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, fakeAcceptKey)
            .build()

        // When
        val result = testedResponse.isStreaming(fakeMimeType)

        // Then
        assertThat(result).isTrue()
        verifyNoInteractions(mockResponseBody)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "\t"])
    fun `M allow finite payload W isStreaming() { missing or blank accept header }`(fakeAcceptKey: String?) {
        // Given
        if (fakeAcceptKey != null) {
            testedResponse = testedResponse.newBuilder()
                .header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, fakeAcceptKey)
                .build()
        }

        // When
        val result = testedResponse.isStreaming("application/json")

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(mockResponseBody)
    }
}
