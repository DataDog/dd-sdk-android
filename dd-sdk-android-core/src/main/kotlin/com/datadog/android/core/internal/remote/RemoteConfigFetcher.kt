/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Fetches the remote configuration document from the Datadog CDN.
 */
internal interface RemoteConfigFetcher {
    /**
     * Fetches the remote configuration document from the given URL.
     *
     * @param url the CDN URL to fetch from.
     * @return the raw JSON response body, or null if the fetch failed.
     */
    @WorkerThread
    fun fetch(url: HttpUrl): String?
}

internal class RemoteConfigNetworkFetcher(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger
) : RemoteConfigFetcher {

    @WorkerThread
    @Suppress("TooGenericExceptionCaught")
    override fun fetch(url: HttpUrl): String? {
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

    private fun handleResponse(response: Response, url: HttpUrl): String? {
        return if (response.isSuccessful) {
            @Suppress("UnsafeThirdPartyFunctionCall") // safe: wrapped in outer try-catch
            val body = response.body?.use { it.string() }
            if (body.isNullOrEmpty()) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_EMPTY_BODY },
                    additionalProperties = mapOf(ATTR_URL to url.toString())
                )
                null
            } else {
                body
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
        internal const val ATTR_RESPONSE_CODE = "response_code"
        internal const val ATTR_URL = "url"
    }
}
