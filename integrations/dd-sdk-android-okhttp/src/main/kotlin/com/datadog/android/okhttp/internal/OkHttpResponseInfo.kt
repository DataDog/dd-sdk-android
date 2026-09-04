/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpBodySnapshot
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.instrumentation.network.PeekableBodyInfo
import com.datadog.android.internal.network.HttpSpec
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

internal class OkHttpResponseInfo(
    internal val originalResponse: Response,
    internal val internalLogger: InternalLogger
) : HttpResponseInfo, PeekableBodyInfo {

    override val contentType: String?
        get() = originalResponse.body?.contentType()?.let {
            // manually rebuild the mimetype as `toString()` can also include the charsets
            it.type + "/" + it.subtype
        }

    override val statusCode: Int get() = originalResponse.code

    override val url: String get() = originalResponse.request.url.toString()

    override val headers: Map<String, List<String>> get() = originalResponse.headers.toMultimap()

    @get:WorkerThread
    override val contentLength: Long?
        get() = try {
            // if there is a Content-Length available, we can read it directly
            // however, OkHttp will drop Content-Length header if transparent compression is
            // used (since the value reported cannot be applied to decompressed body), so to be
            // able to still read it, we force decompression by calling peekBody
            originalResponse.body?.contentLengthOrNull()
                ?: originalResponse.peekBody(MAX_BODY_PEEK_BYTES).contentLengthOrNull()
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_BODY },
                e
            )
            null
        } catch (e: IllegalStateException) {
            // this happens if we cannot read body at all (ex. WebSocket, etc.), no need to report to telemetry
            internalLogger.log(
                InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_BODY },
                e
            )
            null
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PEEK_BODY },
                e
            )
            null
        }

    override val request: HttpRequestInfo get() = OkHttpRequestInfo(originalResponse.request, internalLogger)

    @WorkerThread
    @Suppress("ReturnCount")
    override fun peekBody(maxBytes: Long): HttpBodySnapshot? {
        if (maxBytes <= 0) return null
        val body = originalResponse.body ?: return null

        val limit = maxBytes.coerceAtMost(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES)

        return try {
            // contentType() is open, so a custom ResponseBody can throw from it, as can reading the
            // payload itself. Everything that touches the body stays inside this try.
            val mediaType = body.contentType()
            val bodyContentType = mediaType?.toString()
            val mimeType = mediaType?.let { it.type + "/" + it.subtype }

            // A stream only ends when the peer closes it, so reading one here would block for as
            // long as it stays open, and the payload it carries is unbounded.
            if (HttpSpec.ContentType.isStream(mimeType) || isWebSocket()) return null

            // one byte over the limit, so that a payload landing exactly on it isn't flagged as
            // truncated, and anything bigger reliably is
            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught below
            val peeked = originalResponse.peekBody(limit + 1)

            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught below
            val available = peeked.contentLength().coerceAtLeast(0L)
            val isTruncated = available > limit

            // read exactly what is kept, rather than reading limit + 1 bytes and copying the
            // wanted prefix back out of them
            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught below
            val bytes = peeked.source().readByteArray(available.coerceAtMost(limit))

            HttpBodySnapshot(
                bytes = bytes,
                contentType = bodyContentType,
                isTruncated = isTruncated
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Custom response bodies may throw from any open method. A failed peek should be
            // visible to maintainers but must never break the application's response handling.
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_BODY },
                e
            )
            null
        }
    }

    private fun isWebSocket(): Boolean =
        !originalResponse.header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null).isNullOrBlank()

    internal companion object {

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK_BYTES: Long = 32 * 1024L * 1024L

        internal const val ERROR_PEEK_BODY = "Unable to peek response body."

        private fun ResponseBody.contentLengthOrNull(): Long? = contentLength().takeIf { it >= 0L }
    }
}
