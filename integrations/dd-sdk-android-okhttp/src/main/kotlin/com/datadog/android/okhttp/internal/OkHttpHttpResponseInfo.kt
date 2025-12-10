/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

internal class OkHttpHttpResponseInfo(
    internal val response: Response,
    internal val internalLogger: InternalLogger
) : HttpResponseInfo {

    override val contentType: String?
        get() = response.body?.contentType()?.let {
            // manually rebuild the mimetype as `toString()` can also include the charsets
            it.type + "/" + it.subtype
        }

    override val statusCode: Int get() = response.code

    override val url: String get() = response.request.url.toString()

    override val headers: Map<String, List<String>> get() = response.headers.toMultimap()

    override val contentLength: Long?
        get() = try {
            // if there is a Content-Length available, we can read it directly
            // however, OkHttp will drop Content-Length header if transparent compression is
            // used (since the value reported cannot be applied to decompressed body), so to be
            // able to still read it, we force decompression by calling peekBody
            response.body?.contentLengthOrNull() ?: response.peekBody(MAX_BODY_PEEK_BYTES).contentLengthOrNull()
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

    internal companion object {

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK_BYTES: Long = 32 * 1024L * 1024L

        internal const val ERROR_PEEK_BODY = "Unable to peek response body."

        private fun ResponseBody.contentLengthOrNull(): Long? = contentLength().takeIf { it >= 0L }
    }
}
