/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal abstract class DataOkHttpUploaderV2Test<T : DataOkHttpUploaderV2> {

    lateinit var testedUploader: T

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockAndroidInfoProvider: AndroidInfoProvider

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpoint: String

    @StringForgery
    lateinit var fakeData: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeSource: String

    @StringForgery
    lateinit var fakeSdkVersion: String

    lateinit var fakeSystemUserAgent: String
    lateinit var fakeSdkUserAgent: String

    lateinit var fakeResponse: Response

    @StringForgery
    lateinit var fakeDeviceModel: String

    @StringForgery
    lateinit var fakeDeviceBuildId: String

    @StringForgery
    lateinit var fakeDeviceVersion: String

    @BeforeEach
    open fun `set up`(forge: Forge) {
        whenever(mockCallFactory.newCall(any())) doReturn mockCall

        whenever(mockAndroidInfoProvider.osVersion) doReturn fakeDeviceVersion
        whenever(mockAndroidInfoProvider.deviceModel) doReturn fakeDeviceModel
        whenever(mockAndroidInfoProvider.deviceBuildId) doReturn fakeDeviceBuildId

        fakeSystemUserAgent = if (forge.aBool()) forge.anAlphaNumericalString() else ""
        System.setProperty("http.agent", fakeSystemUserAgent)
        fakeSdkUserAgent = "Datadog/$fakeSdkVersion " +
            "(Linux; U; Android $fakeDeviceVersion; " +
            "$fakeDeviceModel Build/$fakeDeviceBuildId)"

        testedUploader = buildTestedInstance(mockCallFactory)
    }

    abstract fun buildTestedInstance(callFactory: Call.Factory): T

    abstract fun expectedPath(): String

    abstract fun expectedQueryParams(source: String): Map<String, String>

    // region Expected status codes

    @Test
    fun `𝕄 return success 𝕎 upload() {202 accepted status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(202, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error 𝕎 upload() {400 bad request status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(400, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client token error 𝕎 upload() {401 unauthorized status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(401, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error 𝕎 upload() {403 forbidden status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(403, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error with retry 𝕎 upload() {408 timeout status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(408, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_RATE_LIMITING)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error 𝕎 upload() {413 too large status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(413, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error with retry 𝕎 upload() {429 too many requests status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(429, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_RATE_LIMITING)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return server error 𝕎 upload() {500 internal error status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(500, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return server error 𝕎 upload() {503 unavailable status}`(
        @StringForgery message: String
    ) {
        // Given
        whenever(mockCall.execute()) doReturn mockResponse(503, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
    }

    // endregion

    // region Unknown cases

    @RepeatedTest(32)
    fun `𝕄 return unknown error 𝕎 upload() {xxx status}`(
        forge: Forge,
        @StringForgery message: String
    ) {
        // Given
        var statusCode: Int
        do {
            statusCode = forge.anInt(200, 600)
        } while (statusCode in arrayOf(202, 400, 401, 403, 408, 413, 429, 500, 503))
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.UNKNOWN_ERROR)
        verifyRequest()
        verifyResponseIsClosed()
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

    // endregion

    // region Invalid Headers

    @Test
    fun `𝕄 fail and warn 𝕎 upload() {invalid API_KEY header}`(
        @StringForgery(regex = "[a-z]+[\u007F-\u00FF]+") invalidValue: String
    ) {
        // Given
        fakeClientToken = invalidValue
        testedUploader = buildTestedInstance(mockCallFactory)

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verify(mockCallFactory, never()).newCall(any())
    }

    @Test
    fun `𝕄 redact invalid source header 𝕎 upload() {invalid source header}`(
        @StringForgery(regex = "[a-z]+") validValue: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValuePostfix: String
    ) {
        // Given
        fakeSource = validValue + invalidValuePostfix
        testedUploader = buildTestedInstance(mockCallFactory)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(
            expectedHeaders(source = validValue),
            validValue
        )
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 redact invalid sdk version header 𝕎 upload() {invalid sdk version, valid user agent}`(
        @StringForgery(regex = "[a-z]+") validValue: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValuePostfix: String,
        @StringForgery userAgent: String
    ) {
        // Given
        fakeSdkVersion = validValue + invalidValuePostfix
        fakeSystemUserAgent = userAgent
        System.setProperty("http.agent", fakeSystemUserAgent)
        testedUploader = buildTestedInstance(mockCallFactory)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(
            expectedHeaders(sdkVersion = validValue)
        )
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 redact invalid sdk version header 𝕎 upload() {invalid sdk version and user agent }`(
        @StringForgery(regex = "[a-z]+") validValue: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValuePostfix: String
    ) {
        // Given
        fakeSdkVersion = validValue + invalidValuePostfix
        fakeSystemUserAgent = ""
        System.setProperty("http.agent", fakeSystemUserAgent)
        testedUploader = buildTestedInstance(mockCallFactory)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(
            expectedHeaders(
                sdkVersion = validValue,
                userAgent = "Datadog/$validValue " +
                    "(Linux; U; Android $fakeDeviceVersion; " +
                    "$fakeDeviceModel Build/$fakeDeviceBuildId)"
            )
        )
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 redact invalid user agent header 𝕎 upload() {invalid source header}`(
        @StringForgery(regex = "[a-z]+") validValue: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValuePostfix: String
    ) {
        // Given
        fakeSystemUserAgent = validValue + invalidValuePostfix
        System.setProperty("http.agent", fakeSystemUserAgent)
        testedUploader = buildTestedInstance(mockCallFactory)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(
            expectedHeaders(userAgent = validValue)
        )
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 replace fully invalid user agent header 𝕎 upload() {invalid source header}`(
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValue: String
    ) {
        // Given
        fakeSystemUserAgent = invalidValue
        System.setProperty("http.agent", fakeSystemUserAgent)
        testedUploader = buildTestedInstance(mockCallFactory)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeData.toByteArray(Charsets.UTF_8))

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(
            expectedHeaders(userAgent = fakeSdkUserAgent)
        )
        verifyResponseIsClosed()
    }

    // endregion

    // region Internal

    private fun mockResponse(statusCode: Int, message: String): Response {
        fakeResponse = Response.Builder()
            .request(Request.Builder().url(fakeEndpoint).get().build())
            .code(statusCode)
            .message(message)
            .protocol(Protocol.HTTP_2)
            .body(mock())
            .build()
        return fakeResponse
    }

    private fun verifyRequest(
        expectedHeaders: Map<String, String> = expectedHeaders(),
        source: String = fakeSource
    ) {
        argumentCaptor<Request> {
            verify(mockCallFactory).newCall(capture())

            verifyRequestUrl(firstValue.url(), source)
            verifyRequestHeaders(firstValue.headers(), expectedHeaders)
            verifyRequestIdHeaders(firstValue.headers())
            verifyRequestBody(firstValue.body())
        }
    }

    private fun verifyRequestUrl(url: HttpUrl, source: String) {
        assertThat("${url.scheme()}://${url.host()}").isEqualTo(fakeEndpoint)
        assertThat(url.encodedPath()).isEqualTo(expectedPath())

        val expectedQueryParams = expectedQueryParams(source)

        if (expectedQueryParams.isEmpty()) {
            assertThat(url.query()).isNullOrEmpty()
        } else {
            expectedQueryParams.forEach { (k, v) ->
                assertThat(url.queryParameter(k))
                    .overridingErrorMessage(
                        "Expected query parameter $k to be equal to [$v]\n" +
                            "but was [${url.queryParameter(k)}]"
                    )
                    .isEqualTo(v)
            }
        }
    }

    private fun verifyRequestBody(body: RequestBody?) {
        checkNotNull(body)
        assertThat(body.contentType()).isNull()
        assertThat(body.contentLength()).isEqualTo(fakeData.length.toLong())
    }

    private fun verifyRequestHeaders(
        headers: Headers,
        expectedHeaders: Map<String, String>
    ) {
        expectedHeaders.forEach { (key, value) ->
            assertThat(headers.get(key)).isEqualTo(value)
        }
    }

    private fun verifyRequestIdHeaders(headers: Headers) {
        assertThat(headers.get("DD-REQUEST-ID")).matches {
            UUID.fromString(it) != UUID(0, 0)
        }
    }

    private fun expectedHeaders(
        clientToken: String = fakeClientToken,
        source: String = fakeSource,
        sdkVersion: String = fakeSdkVersion,
        userAgent: String = fakeSystemUserAgent.ifBlank { fakeSdkUserAgent }
    ): Map<String, String> {
        return mapOf(
            "DD-API-KEY" to clientToken,
            "DD-EVP-ORIGIN" to source,
            "DD-EVP-ORIGIN-VERSION" to sdkVersion,
            "Content-Type" to testedUploader.contentType,
            "User-Agent" to userAgent
        )
    }

    private fun verifyResponseIsClosed() {
        verify(fakeResponse.body())!!.close()
    }

    // endregion
}
