/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(Configurator::class)
internal class RequestUniqueIdentifierTest {

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/\\w+")
    private lateinit var fakeUrl: String

    @StringForgery(regex = "x-[a-z]+/[a-z]+")
    private lateinit var fakeContentType: String

    @StringForgery
    private lateinit var fakeBody: String

    @Test
    fun `identify GET request`() {
        val request = Request.Builder()
            .get().url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        assertThat(id).isEqualTo("GET•$fakeUrl")
    }

    @Test
    fun `identify POST request`() {
        val body = RequestBody.create(null, fakeBody)
        val request = Request.Builder()
            .post(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("POST•$fakeUrl•$contentLength•null")
    }

    @Test
    fun `identify POST request with content type`() {
        val body = RequestBody.create(MediaType.parse(fakeContentType), fakeBody)
        val request = Request.Builder()
            .post(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("POST•$fakeUrl•$contentLength•$fakeContentType; charset=utf-8")
    }

    @Test
    fun `identify PUT request`() {
        val body = RequestBody.create(null, fakeBody)
        val request = Request.Builder()
            .put(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("PUT•$fakeUrl•$contentLength•null")
    }

    @Test
    fun `identify PUT request with content type`() {
        val body = RequestBody.create(MediaType.parse(fakeContentType), fakeBody)
        val request = Request.Builder()
            .put(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("PUT•$fakeUrl•$contentLength•$fakeContentType; charset=utf-8")
    }

    @Test
    fun `identify PATCH request`() {
        val body = RequestBody.create(null, fakeBody)
        val request = Request.Builder()
            .patch(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("PATCH•$fakeUrl•$contentLength•null")
    }

    @Test
    fun `identify PATCH request with content type`() {
        val body = RequestBody.create(MediaType.parse(fakeContentType), fakeBody)
        val request = Request.Builder()
            .patch(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("PATCH•$fakeUrl•$contentLength•$fakeContentType; charset=utf-8")
    }

    @Test
    fun `identify DELETE request`() {
        val request = Request.Builder()
            .delete().url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        assertThat(id)
            .isEqualTo("DELETE•$fakeUrl")
    }

    @Test
    fun `identify DELETE request with body`() {
        val body = RequestBody.create(null, fakeBody)
        val request = Request.Builder()
            .delete(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("DELETE•$fakeUrl•$contentLength•null")
    }

    @Test
    fun `identify DELETE request with content type`() {
        val body = RequestBody.create(MediaType.parse(fakeContentType), fakeBody)
        val request = Request.Builder()
            .delete(body).url(fakeUrl)
            .build()

        val id = identifyRequest(request)

        val contentLength = fakeBody.length
        assertThat(id)
            .isEqualTo("DELETE•$fakeUrl•$contentLength•$fakeContentType; charset=utf-8")
    }

    @ValueSource(strings = ["POST", "PUT", "PATCH", "DELETE"])
    @ParameterizedTest
    fun `identify request { body#contentLength throws exception }`(
        method: String
    ) {
        val body = object : RequestBody() {
            override fun contentLength(): Long {
                throw IOException("")
            }

            override fun contentType(): MediaType? {
                return MediaType.parse(fakeContentType)
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

        val id = identifyRequest(request)

        assertThat(id)
            .isEqualTo("$method•$fakeUrl•0•$fakeContentType")
    }
}
