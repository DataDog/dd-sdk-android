/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.UnknownHostException
import java.util.Locale
import com.datadog.android.api.net.Request as DatadogRequest

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
    lateinit var mockLogger: InternalLogger

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
    fun `M redact invalid user agent header W upload()`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery(regex = "[a-z]+") validValue: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValuePostfix: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()

        fakeSystemUserAgent = validValue + invalidValuePostfix
        System.setProperty("http.agent", fakeSystemUserAgent)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.Success::class.java)
        assertThat(result.code).isEqualTo(202)
        verifyRequest(fakeDatadogRequest, expectedUserAgentHeader = validValue)
        verifyResponseIsClosed()
    }

    @Test
    fun `M replace fully invalid user agent header W upload()`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValue: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()

        fakeSystemUserAgent = invalidValue
        System.setProperty("http.agent", fakeSystemUserAgent)
        whenever(mockCall.execute()) doReturn mockResponse(202, "{}")

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.Success::class.java)
        assertThat(result.code).isEqualTo(202)
        verifyRequest(fakeDatadogRequest, expectedUserAgentHeader = fakeSdkUserAgent)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client token error W upload() { bad api key format }`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery(regex = "[\u007F-\u00FF]+") invalidValue: String,
        forge: Forge
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        fakeDatadogRequest = fakeDatadogRequest.copy(
            headers = fakeDatadogRequest.headers.toMutableMap().apply {
                put(RequestFactory.HEADER_API_KEY, forge.anElementFrom("", invalidValue))
            }
        )
        whenever(mockRequestFactory.create(fakeContext, batch, batchMetadata)) doReturn
            fakeDatadogRequest

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.InvalidTokenError::class.java)
        assertThat(result.code).isEqualTo(UploadStatus.UNKNOWN_RESPONSE_CODE)
        verifyNoInteractions(mockCallFactory)
    }

    // region Expected status codes

    @Test
    fun `M return success W upload() {202 accepted status} `(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(202, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.Success::class.java)
        assertThat(result.code).isEqualTo(202)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client error W upload() {400 bad request status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(400, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpClientError::class.java)
        assertThat(result.code).isEqualTo(400)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client token error W upload() {401 unauthorized status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(401, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.InvalidTokenError::class.java)
        assertThat(result.code).isEqualTo(401)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client error W upload() {403 forbidden status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(403, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.InvalidTokenError::class.java)
        assertThat(result.code).isEqualTo(403)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client error with retry W upload() {408 timeout status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(408, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpClientRateLimiting::class.java)
        assertThat(result.code).isEqualTo(408)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client error W upload() {413 too large status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(413, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpClientError::class.java)
        assertThat(result.code).isEqualTo(413)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return client error with retry W upload() {429 too many requests status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(429, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpClientRateLimiting::class.java)
        assertThat(result.code).isEqualTo(429)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return server error W upload() {500 internal error status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(500, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpServerError::class.java)
        assertThat(result.code).isEqualTo(500)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return server error W upload() {502 bad gateway status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(502, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpServerError::class.java)
        assertThat(result.code).isEqualTo(502)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return server error W upload() {503 unavailable status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(503, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpServerError::class.java)
        assertThat(result.code).isEqualTo(503)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return server error W upload() {504 gateway timeout status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(504, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpServerError::class.java)
        assertThat(result.code).isEqualTo(504)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return server error W upload() {507 insufficient storage status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(507, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.HttpServerError::class.java)
        assertThat(result.code).isEqualTo(507)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    // endregion

    // region Unknown cases

    @RepeatedTest(32)
    fun `M return unknown error W upload() {xxx status}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        forge: Forge,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()

        var statusCode: Int
        do {
            statusCode = forge.anInt(200, 600)
        } while (statusCode in arrayOf(202, 400, 401, 403, 408, 413, 429, 500, 502, 503, 504, 507))
        whenever(mockCall.execute()) doReturn mockResponse(statusCode, message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.UnknownError::class.java)
        assertThat(result.code).isEqualTo(statusCode)
        verifyRequest(fakeDatadogRequest)
        verifyResponseIsClosed()
    }

    @Test
    fun `M return error W upload() {IOException}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doThrow IOException(message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.NetworkError::class.java)
        assertThat(result.code).isEqualTo(UploadStatus.UNKNOWN_RESPONSE_CODE)
        verifyRequest(fakeDatadogRequest)
    }

    @Test
    fun `M return error W upload() {UnknownHostException}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doThrow UnknownHostException(message)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.DNSError::class.java)
        assertThat(result.code).isEqualTo(UploadStatus.UNKNOWN_RESPONSE_CODE)
        verifyRequest(fakeDatadogRequest)
    }

    @Test
    fun `M return error W upload() {any Throwable}`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @Forgery throwable: Throwable
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doThrow throwable

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.NetworkError::class.java)
        assertThat(result.code).isEqualTo(UploadStatus.UNKNOWN_RESPONSE_CODE)
        verifyRequest(fakeDatadogRequest)
    }

    @Test
    fun `M return RequestCreationError W upload() { request factory throws }`(
        @Forgery fakeException: Exception,
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockRequestFactory.create(fakeContext, batch, batchMetadata))
            .doThrow(fakeException)

        // When
        val result = testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        assertThat(result).isInstanceOf(UploadStatus.RequestCreationError::class.java)
        assertThat(result.code).isEqualTo(UploadStatus.UNKNOWN_RESPONSE_CODE)
        verifyNoInteractions(mockCallFactory)
    }

    @Test
    fun `M log the exception to telemetry W upload() { request factory throws }`(
        @Forgery fakeException: Exception,
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockRequestFactory.create(fakeContext, batch, batchMetadata))
            .doThrow(fakeException)

        // When
        testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            "Unable to create the request, probably due to bad data format. " +
                "The batch will be dropped.",
            fakeException
        )
    }

    // endregion

    @Test
    fun `M log warning W upload() { feature request has user-agent header }`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @StringForgery message: String,
        @StringForgery userAgentValue: String,
        forge: Forge
    ) {
        // Given
        val batchMetadata = batchMeta.toByteArray()
        whenever(mockCall.execute()) doReturn mockResponse(202, message)

        fakeDatadogRequest = fakeDatadogRequest.copy(
            headers = fakeDatadogRequest.headers.toMutableMap().apply {
                put(forge.anElementFrom("User-Agent", "user-agent", "UsEr-AgEnT"), userAgentValue)
            }
        )

        whenever(mockRequestFactory.create(fakeContext, batch, batchMetadata)) doReturn
            fakeDatadogRequest

        // When
        testedUploader.upload(fakeContext, batch, batchMetadata)

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
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

            verifyRequestUrl(firstValue.url, expectedRequest.url.toHttpUrl())
            verifyRequestHeaders(
                firstValue.headers,
                expectedRequest.headers,
                expectedUserAgentHeader
            )
            verifyRequestBody(firstValue.body, expectedRequest.body, expectedRequest.contentType)
        }
    }

    private fun verifyRequestUrl(url: HttpUrl, expectedUrl: HttpUrl) {
        assertThat(url.scheme).isEqualTo(expectedUrl.scheme)
        assertThat(url.host).isEqualTo(expectedUrl.host)
        assertThat(url.encodedPath).isEqualTo(expectedUrl.encodedPath)

        val expectedQueryParams = expectedUrl.queryParameterNames

        assertThat(url.queryParameterNames.size).isEqualTo(expectedQueryParams.size)

        if (expectedQueryParams.isEmpty()) {
            assertThat(url.query).isNullOrEmpty()
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
        verify(fakeResponse.body)!!.close()
    }

    // endregion
}
