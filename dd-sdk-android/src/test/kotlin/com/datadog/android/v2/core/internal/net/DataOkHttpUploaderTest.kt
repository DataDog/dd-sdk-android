/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.net

import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.log.Logger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Request as DatadogRequest
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.context.DatadogContext
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.IOException
import java.util.Locale
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataOkHttpUploaderTest {

    private lateinit var testedUploader: DataOkHttpUploader

    @Mock
    lateinit var mockRequestFactory: RequestFactory

    @Mock
    lateinit var mockLogger: Logger

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockAndroidInfoProvider: AndroidInfoProvider

    @StringForgery
    lateinit var fakeSdkVersion: String

    @StringForgery
    lateinit var fakeDeviceModel: String

    @StringForgery
    lateinit var fakeDeviceBuildId: String

    @StringForgery
    lateinit var fakeDeviceVersion: String

    @Forgery
    lateinit var fakeContext: DatadogContext

    @StringForgery(regex = "https://[a-z]+\\.com/api")
    lateinit var fakeEndpoint: String

    @StringForgery
    lateinit var fakeRequestId: String

    @MapForgery(
        key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
        value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
    )
    lateinit var fakeHeaders: Map<String, String>

    @MapForgery(
        key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
        value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
    )
    lateinit var fakeQueryParams: Map<String, String>

    @StringForgery
    lateinit var fakeRequestContext: String

    @StringForgery
    lateinit var fakeRequestBody: String

    lateinit var fakeResponse: Response

    lateinit var fakeDatadogRequest: DatadogRequest

    private lateinit var fakeSystemUserAgent: String

    private lateinit var fakeSdkUserAgent: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockCallFactory.newCall(any())) doReturn mockCall

        whenever(mockAndroidInfoProvider.osVersion) doReturn fakeDeviceVersion
        whenever(mockAndroidInfoProvider.deviceModel) doReturn fakeDeviceModel
        whenever(mockAndroidInfoProvider.deviceBuildId) doReturn fakeDeviceBuildId

        val fakeContentType = forge.anElementFrom(
            "multipart/form-data",
            "application/json",
            "application/x-www-form-urlencoded"
        )
        val url = if (forge.aBool()) {
            fakeEndpoint
        } else {
            fakeEndpoint.plus("?" + fakeQueryParams.map { "${it.key}=${it.value}" })
        }
        // We need to remove duplicates from the map using case-insensitive comparison,
        // because OkHttp will lowercase headers
        fakeHeaders = fakeHeaders.entries
            .groupBy { it.key.lowercase(Locale.US) }
            .mapValues { it.value.first().value }
        fakeDatadogRequest = DatadogRequest(
            id = fakeRequestId,
            description = fakeRequestContext,
            headers = fakeHeaders,
            url = url,
            body = fakeRequestBody.toByteArray(),
            contentType = forge.aNullable { fakeContentType }
        )
        whenever(mockRequestFactory.create(eq(fakeContext), any(), any())) doReturn
            fakeDatadogRequest

        fakeSystemUserAgent = if (forge.aBool()) forge.anAlphaNumericalString() else ""
        System.setProperty("http.agent", fakeSystemUserAgent)

        fakeSdkUserAgent = "Datadog/$fakeSdkVersion " +
            "(Linux; U; Android $fakeDeviceVersion; " +
            "$fakeDeviceModel Build/$fakeDeviceBuildId)"

        testedUploader = DataOkHttpUploader(
            mockRequestFactory,
            mockLogger,
            mockCallFactory,
            fakeSdkVersion,
            mockAndroidInfoProvider
        )
    }

    @Test
    fun `𝕄 redact invalid user agent header 𝕎 upload()`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery(regex = "[a-z]+") validValue: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValuePostfix: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()

        fakeSystemUserAgent = validValue + invalidValuePostfix
        System.setProperty("http.agent", fakeSystemUserAgent)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(fakeDatadogRequest, expectedUserAgentHeader = validValue)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 replace fully invalid user agent header 𝕎 upload()`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValue: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()

        fakeSystemUserAgent = invalidValue
        System.setProperty("http.agent", fakeSystemUserAgent)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(fakeDatadogRequest, expectedUserAgentHeader = fakeSdkUserAgent)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client token error 𝕎 upload() { bad api key format }`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValue: String,
        forge: Forge
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        fakeDatadogRequest = fakeDatadogRequest.copy(
            headers = fakeDatadogRequest.headers.toMutableMap().apply {
                put(RequestFactory.HEADER_API_KEY, forge.anElementFrom("", invalidValue))
            }
        )
        whenever(mockRequestFactory.create(fakeContext, batchData, batchMetadata)) doReturn
            fakeDatadogRequest

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verifyZeroInteractions(mockCallFactory)
    }

    // region Expected status codes

    @Test
    fun `𝕄 return success 𝕎 upload() {202 accepted status} `(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(202, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.SUCCESS)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error 𝕎 upload() {400 bad request status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(400, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client token error 𝕎 upload() {401 unauthorized status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(401, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error 𝕎 upload() {403 forbidden status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(403, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.INVALID_TOKEN_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error with retry 𝕎 upload() {408 timeout status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(408, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_RATE_LIMITING)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error 𝕎 upload() {413 too large status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(413, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return client error with retry 𝕎 upload() {429 too many requests status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(429, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_CLIENT_RATE_LIMITING)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return server error 𝕎 upload() {500 internal error status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(500, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return server error 𝕎 upload() {503 unavailable status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(503, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.HTTP_SERVER_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    // endregion

    // region Unknown cases

    @RepeatedTest(32)
    fun `𝕄 return unknown error 𝕎 upload() {xxx status}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()

        var statusCode: Int
        do {
            statusCode = forge.anInt(200, 600)
        } while (statusCode in arrayOf(202, 400, 401, 403, 408, 413, 429, 500, 503))
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.UNKNOWN_ERROR)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `𝕄 return error 𝕎 upload() {IOException}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doThrow IOException(message)

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.NETWORK_ERROR)
        verifyRequest(fakeDatadogRequest)
    }

    @Test
    fun `𝕄 return error 𝕎 upload() {any Throwable}`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @Forgery throwable: Throwable
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doThrow throwable

        // When
        val result = testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        assertThat(result).isEqualTo(UploadStatus.NETWORK_ERROR)
        verifyRequest(fakeDatadogRequest)
    }

    // endregion

    @Test
    fun `𝕄 log warning 𝕎 upload() { feature request has user-agent header }`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @StringForgery message: String,
        @StringForgery userAgentValue: String,
        forge: Forge
    ) {
        // Given
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(202, message)

        fakeDatadogRequest = fakeDatadogRequest.copy(
            headers = fakeDatadogRequest.headers.toMutableMap().apply {
                put(forge.anElementFrom("User-Agent", "user-agent", "UsEr-AgEnT"), userAgentValue)
            }
        )

        whenever(mockRequestFactory.create(fakeContext, batchData, batchMetadata)) doReturn
            fakeDatadogRequest

        // When
        testedUploader.upload(fakeContext, batchData, batchMetadata)

        // Then
        verify(mockLogger).w(
            DataOkHttpUploader.WARNING_USER_AGENT_HEADER_RESERVED
        )
    }

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
        expectedRequest: DatadogRequest,
        expectedUserAgentHeader: String = fakeSystemUserAgent.ifBlank { fakeSdkUserAgent }
    ) {
        argumentCaptor<Request> {
            verify(mockCallFactory).newCall(capture())

            verifyRequestUrl(firstValue.url(), HttpUrl.get(expectedRequest.url))
            verifyRequestHeaders(
                firstValue.headers(),
                expectedRequest.headers,
                expectedUserAgentHeader
            )
            verifyRequestBody(firstValue.body(), expectedRequest.body, expectedRequest.contentType)
        }
    }

    private fun verifyRequestUrl(url: HttpUrl, expectedUrl: HttpUrl) {
        assertThat(url.scheme()).isEqualTo(expectedUrl.scheme())
        assertThat(url.host()).isEqualTo(expectedUrl.host())
        assertThat(url.encodedPath()).isEqualTo(expectedUrl.encodedPath())

        val expectedQueryParams = expectedUrl.queryParameterNames()

        assertThat(url.queryParameterNames().size).isEqualTo(expectedQueryParams.size)

        if (expectedQueryParams.isEmpty()) {
            assertThat(url.query()).isNullOrEmpty()
        } else {
            expectedQueryParams.forEach {
                val actualValue = url.queryParameter(it)
                val expectedValue = expectedUrl.queryParameter(it)
                assertThat(actualValue)
                    .overridingErrorMessage(
                        "Expected query parameter $it to be equal to [$expectedValue] " +
                            "but was [$actualValue]"
                    )
                    .isEqualTo(expectedValue)
            }
        }
    }

    private fun verifyRequestBody(
        body: RequestBody?,
        expectedBody: ByteArray,
        contentType: String?
    ) {
        checkNotNull(body)
        if (contentType == null) {
            assertThat(body.contentType()).isNull()
        } else {
            assertThat(body.contentType().toString()).isEqualTo(contentType)
        }
        assertThat(body.contentLength()).isEqualTo(expectedBody.size.toLong())
    }

    private fun verifyRequestHeaders(
        headers: Headers,
        expectedHeaders: Map<String, String>,
        expectedUserAgentHeader: String
    ) {
        val actualHeaders = headers.toMultimap()

        assertThat(actualHeaders.values).allMatch { it.size == 1 }

        assertThat(
            actualHeaders
                .mapValues { it.value.first() }
                .filter { !"User-Agent".equals(it.key, ignoreCase = true) }
        )
            .isEqualTo(expectedHeaders.mapKeys { it.key.lowercase(Locale.US) })

        assertThat(headers.get("User-Agent")).isEqualTo(expectedUserAgentHeader)
    }

    private fun verifyResponseIsClosed() {
        verify(fakeResponse.body())!!.close()
    }

    // endregion
}
