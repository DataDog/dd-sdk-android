package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.ResponseInfo
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

internal class OkHttpResponseInfo(
    internal val response: Response
) : ResponseInfo {

    override val contentType: String?
        get() = response.body?.contentType()?.let {
            // manually rebuild the mimetype as `toString()` can also include the charsets
            it.type + "/" + it.subtype
        }

    override val statusCode: Int get() = response.code

    override val url: String get() = response.request.url.toString()

    override val headers: Map<String, List<String>> get() = response.headers.toMultimap()

    override fun computeContentLength(internalLogger: InternalLogger): Long? {
        return try {
            // if there is a Content-Length available, we can read it directly
            // however, OkHttp will drop Content-Length header if transparent compression is
            // used (since the value reported cannot be applied to decompressed body), so to be
            // able to still read it, we force decompression by calling peekBody
            response.body?.contentLengthOrNull() ?: response.peekBody(MAX_BODY_PEEK).contentLengthOrNull()
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
    }

    internal companion object {

        // We need to limit this value as the body will be loaded in memory
        private const val MAX_BODY_PEEK: Long = 32 * 1024L * 1024L

        internal const val ERROR_PEEK_BODY = "Unable to peek response body."

        private fun ResponseBody.contentLengthOrNull(): Long? = contentLength().takeIf { it >= 0L }
    }
}
