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
import org.mockito.kotlin.verifyNoInteractions
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

    // region fetch() - Success, genuine (non-304) fetch

    @Test
    fun `M return FetchResult with headers W fetch() { genuine network 2xx response }`(
        @StringForgery fakeBody: String,
        @StringForgery fakeVersionId: String
    ) {
        // Given
        val fakeResponse = buildResponse(
            code = 200,
            body = fakeBody,
            headers = mapOf(
                "x-amz-version-id" to fakeVersionId,
                "last-modified" to "Wed, 21 Oct 2015 07:28:00 GMT"
            ),
            networkResponseCode = 200
        )
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result?.body).isEqualTo(fakeBody)
        assertThat(result?.versionId).isEqualTo(fakeVersionId)
        assertThat(result?.lastModified).isEqualTo(1445412480000L)
    }

    @Test
    fun `M return FetchResult with null metadata W fetch() { response missing CDN headers }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeResponse = buildResponse(code = 200, body = fakeBody, networkResponseCode = 200)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result?.body).isEqualTo(fakeBody)
        assertThat(result?.versionId).isNull()
        assertThat(result?.lastModified).isNull()
    }

    @Test
    fun `M return null and log W fetch() { 2xx response with empty body }`() {
        // Given
        val fakeResponse = buildResponse(code = 200, body = "", networkResponseCode = 200)
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

    // region fetch() - Success, but nothing new to report

    @Test
    fun `M return null and not log W fetch() { 304 revalidation, cached body unchanged }`(
        @StringForgery fakeBody: String
    ) {
        // Given — OkHttp's cache resolves a 304 revalidation transparently: the merged response
        // is a successful 200 with the previously cached body, but networkResponse reflects the
        // raw 304 exchange.
        val fakeResponse = buildResponse(code = 200, body = fakeBody, networkResponseCode = 304)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return null W fetch() { fully served from cache without a network round trip }`(
        @StringForgery fakeBody: String
    ) {
        // Given — no networkResponse at all: OkHttp served this straight from the cache because
        // it was still fresh, no conditional GET was even made.
        val fakeResponse = buildResponse(code = 200, body = fakeBody, networkResponseCode = null)
        whenever(mockCallFactory.newCall(isA())).doReturn(mockCall)
        whenever(mockCall.execute()).doReturn(fakeResponse)

        // When
        val result = testedFetcher.fetch(fakeUrl.toHttpUrl())

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region fetch() - HTTP errors

    @Test
    fun `M return null and log W fetch() { 4xx response }`(
        @StringForgery fakeBody: String
    ) {
        // Given
        val fakeResponse = buildResponse(code = 404, message = "Not Found", body = fakeBody)
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
        val fakeResponse = buildResponse(code = 500, message = "Internal Server Error", body = fakeBody)
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

    // region test helpers

    /**
     * Builds a [Response] mirroring what OkHttp's [okhttp3.internal.cache.CacheInterceptor]
     * hands back to the caller. [networkResponseCode] models `Response.networkResponse`: null
     * means the response was served fully from cache with no network round trip at all; any
     * code means a network round trip happened (200 = genuine fetch, 304 = revalidation-unchanged).
     */
    private fun buildResponse(
        code: Int,
        body: String,
        message: String = "OK",
        headers: Map<String, String> = emptyMap(),
        networkResponseCode: Int? = null
    ): Response {
        val request = Request.Builder().url(fakeUrl).build()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody("application/json".toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (networkResponseCode != null) {
            val networkResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(networkResponseCode)
                .message(message)
                .build()
            builder.networkResponse(networkResponse)
        }
        return builder.build()
    }

    // endregion
}
