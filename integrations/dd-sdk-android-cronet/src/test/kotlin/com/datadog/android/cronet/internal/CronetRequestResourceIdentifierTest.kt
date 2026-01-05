/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.cronet.DatadogCronetEngine
import com.datadog.android.rum.internal.net.RumResourceInstrumentation.Companion.buildResourceId
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.UploadDataProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetRequestResourceIdentifierTest {

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/\\w+")
    private lateinit var fakeUrl: String

    @StringForgery(regex = "x-[a-z]+/[a-z]+")
    private lateinit var fakeContentType: String

    @StringForgery
    private lateinit var fakeBody: String

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: DatadogRequestCallback

    @Mock
    lateinit var mockExecutor: Executor

    private var fakeContentLength: Long = 0L

    @BeforeEach
    fun `se tup`() {
        fakeContentLength = fakeBody.length.toLong()
    }

    @Test
    fun `M return {GET url} W uniqueId {GET request}`() {
        // Given
        val request = newRequestInfo(method = HttpSpec.Method.GET)

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual).isEqualTo("GET•$fakeUrl")
    }

    @Test
    fun `M return {POST method contentLength null} W uniqueId {POST request with content length}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.POST,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("POST•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {method rul contentLength contentType} W uniqueId {POST request with content length and type}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.POST,
            contentLength = fakeContentLength,
            contentType = fakeContentType
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("POST•$fakeUrl•$fakeContentLength•$fakeContentType")
    }

    @Test
    fun `M return {method url contentLength null} W uniqueId {PUT request with content length}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.PUT,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PUT•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {method url contentLength contentType} W uniqueId {PUT request with content type}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.PUT,
            contentType = fakeContentType,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PUT•$fakeUrl•$fakeContentLength•$fakeContentType")
    }

    @Test
    fun `M return {method url contentLength null} W uniqueId {PATCH request with content length}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.PATCH,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PATCH•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {method url contentLength contentType} W uniqueId {PATCH request with content length and type}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.PATCH,
            contentType = fakeContentType,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("PATCH•$fakeUrl•$fakeContentLength•$fakeContentType")
    }

    @Test
    fun `M return {method url} W uniqueId {DELETE request}`() {
        // Given
        val request = newRequestInfo(method = HttpSpec.Method.DELETE)

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("DELETE•$fakeUrl")
    }

    @Test
    fun `M return {method url contentLength null} W uniqueId {DELETE request with content length}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.DELETE,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("DELETE•$fakeUrl•$fakeContentLength•null")
    }

    @Test
    fun `M return {method url contentLength contentType} W uniqueId {DELETE request with content length and type}`() {
        // Given
        val request = newRequestInfo(
            method = HttpSpec.Method.DELETE,
            contentType = fakeContentType,
            contentLength = fakeContentLength
        )

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("DELETE•$fakeUrl•$fakeContentLength•$fakeContentType")
    }

    @ValueSource(strings = [HttpSpec.Method.POST, HttpSpec.Method.PUT, HttpSpec.Method.PATCH, HttpSpec.Method.DELETE])
    @ParameterizedTest
    fun `M return {method url 0 contentType} W uniqueId {request with exception thrown for content length}`(
        method: String
    ) {
        // Given
        val requestContext = DatadogCronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(method) }

        requestContext.addHeader(HttpSpec.Headers.CONTENT_TYPE, fakeContentType)
        requestContext.setUploadDataProvider(
            mock<UploadDataProvider> { on { length } doThrow IOException("") },
            mockExecutor
        )

        val request = CronetHttpRequestInfo(requestContext)

        // When
        val actual = request.uniqueId

        // Then
        assertThat(actual)
            .isEqualTo("$method•$fakeUrl•0•$fakeContentType")
    }

    private fun newRequestInfo(
        url: String = fakeUrl,
        method: String = HttpSpec.Method.GET,
        contentLength: Long? = null,
        contentType: String? = null
    ): CronetHttpRequestInfo {
        val requestContext = DatadogCronetRequestContext(
            url = url,
            engine = mockEngine,
            datadogRequestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(method) }
        contentType?.let { requestContext.addHeader(HttpSpec.Headers.CONTENT_TYPE, it) }
        contentLength?.let {
            requestContext.setUploadDataProvider(
                mock<UploadDataProvider> { on { length } doReturn contentLength },
                mockExecutor
            )
        }
        return CronetHttpRequestInfo(requestContext)
    }

    private val CronetHttpRequestInfo.uniqueId: String
        get() = buildResourceId(this, false).key
}
