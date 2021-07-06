/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.os.Build
import com.datadog.android.BuildConfig
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import java.io.IOException
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

internal abstract class DataOkHttpUploaderTest<T : DataOkHttpUploader> {

    lateinit var testedUploader: T

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpoint: String

    @StringForgery
    lateinit var fakeData: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    lateinit var fakeUserAgent: String

    @BeforeEach
    open fun `set up`(forge: Forge) {

        whenever(mockCallFactory.newCall(any())) doReturn mockCall

        Build.VERSION::class.java.setStaticValue("RELEASE", forge.anAlphaNumericalString())
        Build::class.java.setStaticValue("MODEL", forge.anAlphabeticalString())
        Build::class.java.setStaticValue("ID", forge.anAlphabeticalString())

        fakeUserAgent = if (forge.aBool()) forge.anAlphaNumericalString() else ""
        System.setProperty("http.agent", fakeUserAgent)

        testedUploader = uploader(mockCallFactory)
    }

    abstract fun uploader(callFactory: Call.Factory): T

    abstract fun expectedPath(): String

    abstract fun expectedQueryParams(): Map<String, String>

    @AfterEach
    open fun `tear down`() {
        Build.VERSION::class.java.setStaticValue("RELEASE", null)
        Build::class.java.setStaticValue("MODEL", null)
        Build::class.java.setStaticValue("ID", null)
    }

    @Test
    fun `𝕄 return error 𝕎 upload() {IOException}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doThrow IOException(message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.NETWORK_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return error 𝕎 upload() {any Throwable}`(
        @Forgery throwable: Throwable
    ) {
        // Given
        whenever(mockCall.execute()) doThrow throwable

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.NETWORK_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return unknown state 𝕎 upload() {1xx status} `(
        @IntForgery(100, 200) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.UNKNOWN_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return success 𝕎 upload() {2xx status} `(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest()
    }

    @Test
    fun `𝕄 return success 𝕎 upload() {3xx status} `(
        @IntForgery(300, 400) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_REDIRECTION)
        verifyRequest()
    }

    @Test
    fun `𝕄 return success 𝕎 upload() {400-402 status} `(
        @IntForgery(400, 403) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return invalid token error 𝕎 upload() {403 status} `(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(403, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return success 𝕎 upload() {404-499 status} `(
        @IntForgery(404, 500) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return success 𝕎 upload() {5xx status} `(
        @IntForgery(500, 600) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        verifyRequest()
    }

    @Test
    fun `𝕄 return success 𝕎 upload() {6xx+ status} `(
        @IntForgery(600) statusCode: Int,
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.UNKNOWN_ERROR)
        verifyRequest()
    }

    // region Internal

    private fun mockResponse(statusCode: Int, message: String): Response {
        return Response.Builder()
            .request(Request.Builder().url(fakeEndpoint).get().build())
            .code(statusCode)
            .message(message)
            .protocol(Protocol.HTTP_2)
            .build()
    }

    private fun verifyRequest() {
        argumentCaptor<Request> {
            verify(mockCallFactory).newCall(capture())

            verifyRequestUrl(firstValue.url())
            verifyRequestHeaders(firstValue.headers())
            verifyRequestBody(firstValue.body())
        }
    }

    private fun verifyRequestUrl(url: HttpUrl) {
        assertThat("${url.scheme()}://${url.host()}").isEqualTo(fakeEndpoint)
        assertThat(url.encodedPath()).isEqualTo(expectedPath())
        expectedQueryParams().forEach { (k, v) ->
            assertThat(url.queryParameter(k)).isEqualTo(v)
        }
    }

    private fun verifyRequestBody(body: RequestBody?) {
        checkNotNull(body)
        assertThat(body.contentType()).isNull()
        assertThat(body.contentLength()).isEqualTo(fakeData.length.toLong())
    }

    private fun verifyRequestHeaders(headers: Headers) {
        assertThat(headers.get("Content-Type")).isEqualTo(testedUploader.contentType)

        val expectedUserAgent = if (fakeUserAgent.isBlank()) {
            "Datadog/${BuildConfig.SDK_VERSION_NAME} " +
                "(Linux; U; Android ${Build.VERSION.RELEASE}; " +
                "${Build.MODEL} Build/${Build.ID})"
        } else {
            fakeUserAgent
        }
        assertThat(headers.get("User-Agent")).isEqualTo(expectedUserAgent)
    }

    // endregion

    companion object {
        const val TIMEOUT_TEST_MS = 250L
        const val THROTTLE_RATE = 8L
        const val THROTTLE_PERIOD_MS = TIMEOUT_TEST_MS * 2
    }
}
