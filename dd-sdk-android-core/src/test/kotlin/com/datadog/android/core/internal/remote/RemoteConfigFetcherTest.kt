/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Cache
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RemoteConfigFetcherTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockHttpCache: Cache

    @TempDir
    lateinit var fakeStorageDir: File

    private lateinit var testedFetcher: RemoteConfigNetworkFetcher

    private val fakeUrl = "https://sdk-configuration.browser-intake-datadoghq.com/v1/fake-id.json"

    @BeforeEach
    fun `set up`() {
        testedFetcher = RemoteConfigNetworkFetcher(
            callFactoryProvider = { _ -> mockCallFactory },
            internalLogger = mockInternalLogger,
            storageDir = fakeStorageDir,
            httpCache = mockHttpCache
        )
    }

    // region fetch() - Success

    @Test
    fun `M return FetchResult with CDN headers W fetch() { 200 with x-amz-version-id and Last-Modified }`(
        @StringForgery fakeBody: String,
        @StringForgery fakeVersionId: String
    ) {
        // Given
        val fakeLastModifiedMs = 1_700_000_000_000L
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("x-amz-version-id", fakeVersionId)
            .header("Last-Modified", "Tue, 14 Nov 2023 22:13:20 GMT") // = 1_700_000_000_000L
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .networkResponse(genuineFetchNetworkResponse(fakeRequest))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.body).isEqualTo(fakeBody)
        assertThat(result.versionId).isEqualTo(fakeVersionId)
        assertThat(result.lastModified).isEqualTo(fakeLastModifiedMs)
    }

    @Test
    fun `M return FetchResult with null CDN headers W fetch() { 200 without optional headers }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .networkResponse(genuineFetchNetworkResponse(fakeRequest))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.body).isEqualTo(fakeBody)
        assertThat(result.versionId).isNull()
        assertThat(result.lastModified).isNull()
    }

    @Test
    fun `M return null W fetch() { fresh cache hit, no network request }`(
        @StringForgery fakeBody: String
    ) {
        // Given — within Cache-Control max-age OkHttp serves the cached body without hitting the
        // network at all, so networkResponse is null. Nothing new was downloaded.
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            // no networkResponse — this is what a fresh cache hit looks like
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then — no new version was downloaded; the service must not touch sync metadata
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W fetch() { 304 response via OkHttp cache }`(
        @StringForgery fakeBody: String
    ) {
        // Given — OkHttp represents a 304 revalidation as a 200 with the cached body,
        // but networkResponse.code reflects the raw 304 exchange.
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeNetworkResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(304)
            .message("Not Modified")
            .build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .networkResponse(fakeNetworkResponse)
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then — 304 means no new data; return null so the service skips metadata update
        assertThat(result).isNull()
    }

    @Test
    fun `M return null and log W fetch() { 2xx response with empty body }`() {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody("application/json".toMediaType()))
            .networkResponse(genuineFetchNetworkResponse(fakeRequest))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_EMPTY_BODY,
            additionalProperties = mapOf(RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString())
        )
        verify(mockHttpCache).evictAll()
    }

    // endregion

    // region fetch() - HTTP errors

    @Test
    fun `M return null and log W fetch() { 4xx response }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_HTTP,
            additionalProperties = mapOf(
                RemoteConfigNetworkFetcher.ATTR_RESPONSE_CODE to 404,
                RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString()
            )
        )
    }

    @Test
    fun `M return null and log W fetch() { 5xx response }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeRequest = Request.Builder().url(fakeUrl).build()
        val fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body(fakeBody.toResponseBody("application/json".toMediaType()))
            .build()
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_HTTP,
            additionalProperties = mapOf(
                RemoteConfigNetworkFetcher.ATTR_RESPONSE_CODE to 500,
                RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString()
            )
        )
    }

    // endregion

    // region fetch() - Network errors

    @Test
    fun `M return null and log to maintainer only W fetch() { IOException }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val exception = IOException(fakeMessage)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = RemoteConfigNetworkFetcher.ERROR_NETWORK,
            throwableClass = IOException::class.java,
            additionalProperties = mapOf(RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString())
        )
    }

    @Test
    fun `M return null and log to maintainer and telemetry W fetch() { unexpected exception }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val exception = RuntimeException(fakeMessage)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doThrow(exception)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigNetworkFetcher.ERROR_NETWORK,
            throwableClass = RuntimeException::class.java,
            additionalProperties = mapOf(RemoteConfigNetworkFetcher.ATTR_URL to fakeUrl.toHttpUrl().toString())
        )
    }

    // endregion

    // region stop()

    @Test
    fun `M close httpCache W release()`() {
        // When
        testedFetcher.release()

        // Then
        verify(mockHttpCache).close()
    }

    @Test
    fun `M log warning W release() { cache close throws IOException }`() {
        // Given
        whenever(mockHttpCache.close()).doThrow(IOException("disk error"))

        // When
        testedFetcher.release()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = RemoteConfigNetworkFetcher.ERROR_CLOSE_CACHE,
            throwableClass = IOException::class.java
        )
    }

    // endregion

    // region evictCache()

    @Test
    fun `M evict httpCache W evictCache()`() {
        // When
        testedFetcher.evictCache()

        // Then
        verify(mockHttpCache).evictAll()
    }

    @Test
    fun `M log warning W evictCache() { evictAll throws IOException }`() {
        // Given
        whenever(mockHttpCache.evictAll()).doThrow(IOException("disk error"))

        // When
        testedFetcher.evictCache()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = RemoteConfigNetworkFetcher.ERROR_EVICT_CACHE,
            throwableClass = IOException::class.java
        )
    }

    // endregion

    // region Helpers

    /**
     * A networkResponse with code 200, marking the outer response as a genuine (non-cached) fetch.
     * OkHttp leaves networkResponse null for a fresh cache hit and sets it to 304 for a revalidation.
     */
    private fun genuineFetchNetworkResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
    }

    // endregion
}
