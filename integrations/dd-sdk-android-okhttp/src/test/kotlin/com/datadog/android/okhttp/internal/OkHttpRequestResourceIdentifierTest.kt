/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class OkHttpRequestResourceIdentifierTest {

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/\\w+")
    private lateinit var fakeUrl: String

    @StringForgery(regex = "x-[a-z]+/[a-z]+")
    private lateinit var fakeContentType: String

    @StringForgery
    private lateinit var fakeBody: String

    private var fakeContentLength: Int = 0

    @BeforeEach
    fun `set up`() {
        fakeContentLength = fakeBody.length
    }

    @Test
    fun `M return {GET url} W uniqueId {GET request}`() {
        // Given
        val request = Request.Builder()
            .get().url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual).isEqualTo("GET•$fakeUrl")
    }

    @Test
    fun `M return {POST url contentLength null} W uniqueId {POST request with body}`() {
        // Given
        val body = fakeBody.toRequestBody(null)
        val request = Request.Builder()
            .post(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("POST•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {POST url contentLength contentType} W uniqueId {POST request with body and content type}`() {
        // Given
        val body = fakeBody.toRequestBody(fakeContentType.toMediaTypeOrNull())
        val request = Request.Builder()
            .post(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("POST•$fakeUrl•$fakeContentLength•$fakeContentType; charset=utf-8")
    }

    @Test
    fun `M return {PUT url contentLength null} W uniqueId {PUT request with body}`() {
        // Given
        val body = fakeBody.toRequestBody(null)
        val request = Request.Builder()
            .put(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PUT•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {PUT url contentLength contentType} W uniqueId {PUT request with body and content type}`() {
        // Given
        val body = fakeBody.toRequestBody(fakeContentType.toMediaTypeOrNull())
        val request = Request.Builder()
            .put(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PUT•$fakeUrl•$fakeContentLength•$fakeContentType; charset=utf-8")
    }

    @Test
    fun `M return {PATCH url contentLength null} W uniqueId {PATCH request with body}`() {
        // Given
        val body = fakeBody.toRequestBody(null)
        val request = Request.Builder()
            .patch(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PATCH•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {PATCH url contentLength contentType} W uniqueId {PATCH request with body and content type}`() {
        // Given
        val body = fakeBody.toRequestBody(fakeContentType.toMediaTypeOrNull())
        val request = Request.Builder()
            .patch(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PATCH•$fakeUrl•$fakeContentLength•$fakeContentType; charset=utf-8")
    }

    @Test
    fun `M return {DELETE url} W uniqueId {DELETE request}`() {
        // Given
        val request = Request.Builder()
            .delete().url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("DELETE•$fakeUrl")
    }

    @Test
    fun `M return {DELETE url contentLength null} W uniqueId {DELETE request with body}`() {
        // Given
        val body = fakeBody.toRequestBody(null)
        val request = Request.Builder()
            .delete(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("DELETE•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {DELETE url contentLength contentType} W uniqueId {DELETE request with body and content type}`() {
        // Given
        val body = fakeBody.toRequestBody(fakeContentType.toMediaTypeOrNull())
        val request = Request.Builder()
            .delete(body).url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("DELETE•$fakeUrl•$fakeContentLength•$fakeContentType; charset=utf-8")
    }

    @ValueSource(strings = ["POST", "PUT", "PATCH", "DELETE"])
    @ParameterizedTest
    fun `M return {method url 0 contentType} W uniqueId {request with exception thrown for content length}`(
        method: String
    ) {
        // Given
        val body = object : RequestBody() {
            override fun contentLength(): Long {
                throw IOException("")
            }

            override fun contentType(): MediaType? {
                return fakeContentType.toMediaTypeOrNull()
            }

            override fun writeTo(sink: BufferedSink) {
                // no-op
            }
        }
        val request = Request.Builder()
            .apply {
                when (method) {
                    "POST" -> post(body)
                    "PUT" -> put(body)
                    "PATCH" -> patch(body)
                    "DELETE" -> delete(body)
                }
            }
            .url(fakeUrl)
            .build()

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("$method•$fakeUrl•0•$fakeContentType")
    }

    private val Request.uniqueId: String
        get() = RumResourceInstrumentation.Companion.buildResourceId(
            request = OkHttpHttpRequestInfo(this),
            generateUuid = false
        ).key
}
