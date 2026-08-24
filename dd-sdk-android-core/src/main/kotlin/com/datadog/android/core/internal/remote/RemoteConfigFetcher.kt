/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.tools.annotation.NoOpImplementation
import okhttp3.Cache
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

/**
 * Fetches the remote configuration document from the Datadog CDN.
 */
@NoOpImplementation
internal interface RemoteConfigFetcher {

    /**
     * The outcome of a genuinely new (non-304) fetch: a fresh body along with the CDN metadata
     * needed to track sync/apply telemetry for the version it belongs to.
     */
    data class FetchResult(
        val body: String,
        /** Value of the `x-amz-version-id` response header, or null if absent. */
        val versionId: String?,
        /** Parsed value of the `Last-Modified` response header in ms from epoch, or null if absent. */
        val lastModified: Long?
    )

    /**
     * Fetches the remote configuration document from the given URL.
     *
     * @param url the CDN URL to fetch from.
     * @return the fetch result if a new (non-304) version was downloaded, or null if the fetch
     * failed or the cached version is still up to date (conditional GET resolved to a 304).
     */
    @WorkerThread
    fun fetch(url: HttpUrl): FetchResult?

    /**
     * Releases any resources held by this fetcher (e.g. HTTP cache).
     */
    fun release()

    /**
     * Evicts all entries from the HTTP cache, forcing a full network re-fetch on the next call.
     * Should be called when a fetched response cannot be parsed, to prevent the bad response
     * from being served from cache on subsequent launches.
     */
    fun evictCache()
}

internal class RemoteConfigNetworkFetcher(
    callFactoryProvider: (Cache) -> Call.Factory,
    private val internalLogger: InternalLogger,
    storageDir: File,
    // only for unit tests
    private val httpCache: Cache = Cache(
        directory = File(storageDir, HTTP_CACHE_DIR_NAME),
        maxSize = HTTP_CACHE_MAX_SIZE
    )
) : RemoteConfigFetcher {

    private val callFactory: Call.Factory = callFactoryProvider(httpCache)

    @WorkerThread
    @Suppress("TooGenericExceptionCaught")
    override fun fetch(url: HttpUrl): RemoteConfigFetcher.FetchResult? {
        @Suppress("UnsafeThirdPartyFunctionCall") // safe: url is a valid HttpUrl, get() has no preconditions
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // safe: wrapped in outer try-catch
            val response = callFactory.newCall(request).execute()
            handleResponse(response, url)
        } catch (e: IOException) {
            // Device is likely offline — log to maintainer only, no telemetry noise.
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { ERROR_NETWORK },
                e,
                additionalProperties = mapOf(ATTR_URL to url.toString())
            )
            null
        } catch (e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_NETWORK },
                e,
                additionalProperties = mapOf(ATTR_URL to url.toString())
            )
            null
        }
    }

    override fun release() {
        try {
            httpCache.close()
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { ERROR_CLOSE_CACHE },
                e
            )
        }
    }

    override fun evictCache() {
        // DiskLruCache.evictAll() is internally synchronized — safe to call even after close()
        try {
            httpCache.evictAll()
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { ERROR_EVICT_CACHE },
                e
            )
        }
    }

    private fun handleResponse(response: Response, url: HttpUrl): RemoteConfigFetcher.FetchResult? {
        return if (response.isSuccessful) {
            // OkHttp serves cached content transparently as a successful 200, but networkResponse
            // reveals what actually happened on the wire: null for a fresh cache hit (no request
            // made at all), 304 for a revalidation. In both cases nothing new was downloaded.
            val networkResponse = response.networkResponse
            if (networkResponse == null || networkResponse.code == HTTP_NOT_MODIFIED) {
                @Suppress("UnsafeThirdPartyFunctionCall") // safe: wrapped in outer try-catch
                response.body?.close()
                return null
            }
            @Suppress("UnsafeThirdPartyFunctionCall") // safe: wrapped in outer try-catch
            val body = response.body?.use { it.string() }
            if (body.isNullOrEmpty()) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_EMPTY_BODY },
                    additionalProperties = mapOf(ATTR_URL to url.toString())
                )
                // Evict the cached empty response so the next syncWithRemote()
                // re-fetches from the network rather than serving the empty body again.
                evictCache()
                null
            } else {
                val lastModified = response.headers.getDate(HEADER_LAST_MODIFIED)?.time
                RemoteConfigFetcher.FetchResult(
                    body = body,
                    versionId = response.header(HEADER_VERSION_ID),
                    lastModified = lastModified
                )
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_HTTP },
                additionalProperties = mapOf(
                    ATTR_RESPONSE_CODE to response.code,
                    ATTR_URL to url.toString()
                )
            )
            @Suppress("UnsafeThirdPartyFunctionCall") // safe: wrapped in outer try-catch
            response.body?.close()
            null
        }
    }

    internal companion object {
        internal const val ERROR_NETWORK = "Remote config fetch failed due to a network error"
        internal const val ERROR_HTTP = "Remote config fetch failed with an HTTP error"
        internal const val ERROR_EMPTY_BODY = "Remote config response body is empty"
        internal const val ERROR_CLOSE_CACHE = "Failed to close remote config HTTP cache"
        internal const val ERROR_EVICT_CACHE = "Failed to evict remote config HTTP cache"
        internal const val ATTR_RESPONSE_CODE = "response_code"
        internal const val ATTR_URL = "url"
        internal const val HTTP_CACHE_DIR_NAME = "rc-http-cache"
        internal const val HTTP_NOT_MODIFIED = 304
        internal const val HEADER_VERSION_ID = "x-amz-version-id"
        internal const val HEADER_LAST_MODIFIED = "Last-Modified"

        // One RC entry is ~1.4 KB (437-byte body + headers + journal). 50 KB gives ~35x headroom
        // to accommodate future schema growth without revisiting this value.
        internal const val HTTP_CACHE_MAX_SIZE = 50L * 1024
    }
}
