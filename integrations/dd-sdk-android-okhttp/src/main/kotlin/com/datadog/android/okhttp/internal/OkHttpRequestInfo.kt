/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.ExtendedRequestInfo
import com.datadog.android.api.instrumentation.network.HttpBodySnapshot
import com.datadog.android.api.instrumentation.network.HttpRequestBody
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.api.instrumentation.network.PeekableBodyInfo
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.resource.ResourceId
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Timeout
import okio.buffer
import java.util.UUID

@Deprecated(
    "This code will be replaced with RequestExt.kt and OkHttpHttpRequestInfo in the further releases.",
    replaceWith = ReplaceWith(
        "rumNetworkInstrumentation.buildResourceId(" +
            "OkHttpHttpRequestInfo(request).buildResourceId(generateUuid)" +
            ")"
    )
)
internal fun Request.buildResourceId(generateUuid: Boolean): ResourceId {
    val uuid = tag(UUID::class.java) ?: (if (generateUuid) UUID.randomUUID() else null)

    @Suppress("DEPRECATION")
    val key = identifyRequest(this)

    return ResourceId(key, uuid?.toString())
}

@Deprecated(
    "This code will be replaced with RequestExt.kt and OkHttpHttpRequestInfo in the further releases.",
    replaceWith = ReplaceWith(
        "rumNetworkInstrumentation.buildResourceId(" +
            "OkHttpHttpRequestInfo(request).buildResourceId(generateUuid)" +
            ")"
    )
)
internal fun identifyRequest(request: Request): String {
    val method = request.method
    val url = request.url
    val body = request.body
    return if (body == null) {
        "$method•$url"
    } else {
        val contentLength = try {
            body.contentLength()
        } catch (@Suppress("SwallowedException") ioe: java.io.IOException) {
            0
        }
        val contentType = body.contentType()
        // TODO RUM-648 It is possible that if requests are say GZIPed (as an example), or maybe
        //  streaming case (?), they all will have contentLength = -1, so if they target the same URL
        //  they are going to have same identifier, messing up reporting.
        //  -1 is valid return value for contentLength() call according to the docs and stands
        //  for "unknown" case.
        //  We need to have a more precise identification.
        if (contentType != null || contentLength != 0L) {
            "$method•$url•$contentLength•$contentType"
        } else {
            "$method•$url"
        }
    }
}

/**
 * [HttpRequestInfo] implementation backed by an OkHttp [Request].
 */
internal class OkHttpRequestInfo(
    internal val originalRequest: Request,
    internal val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : HttpRequestInfo,
    ExtendedRequestInfo,
    MutableHttpRequestInfo,
    PeekableBodyInfo {

    override val method: String get() = originalRequest.method
    override val url: String get() = originalRequest.url.toString()
    override val headers: Map<String, List<String>> get() = originalRequest.headers.toMultimap()
    override val contentType: String? get() = originalRequest.body?.contentType()?.toString()
    override fun <T> tag(type: Class<out T>): T? = originalRequest.tag(type)
    override fun contentLength(): Long? = try {
        originalRequest.body?.contentLength()
    } catch (@Suppress("SwallowedException") _: IOException) {
        null
    }

    @WorkerThread
    @Suppress("ReturnCount")
    override fun peekBody(maxBytes: Long): HttpBodySnapshot? {
        if (maxBytes <= 0) return null
        val body = originalRequest.body ?: return null

        val sink = TruncatingSink(maxBytes.coerceAtMost(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES))
        var bodyContentType: String? = null

        return try {
            // isOneShot(), isDuplex(), contentType() and writeTo() are all open, so a custom
            // RequestBody can throw from any of them. Peeking must never break the request the
            // application is making, so every call that touches the body stays inside this try.
            if (!body.isRewindable()) return null
            bodyContentType = body.contentType()?.toString()

            @Suppress("UnsafeThirdPartyFunctionCall") // sink is non-null, so no NPE
            val bufferedSink = sink.buffer()

            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught below
            body.writeTo(bufferedSink)

            @Suppress("UnsafeThirdPartyFunctionCall") // exceptions are caught below
            bufferedSink.flush()

            sink.snapshot(bodyContentType)
        } catch (@Suppress("SwallowedException") _: TruncationLimitReached) {
            // the sink stopped the body from producing the part that would have been discarded, so
            // what it captured before that is a complete, truncated snapshot
            sink.snapshot(bodyContentType)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { ERROR_PEEK_REQUEST_BODY },
                e
            )
            null
        }
    }

    /**
     * Whether this body can be written more than once, and so can be written here without spoiling
     * the request that follows.
     */
    private fun RequestBody.isRewindable(): Boolean = when {
        // A one-shot body can only be written once, and a duplex body is written while the response
        // is being read. Writing either one here would break the request that is about to be sent.
        isOneShot() || isDuplex() -> false
        // MultipartBody does not override isOneShot(), so it reports itself as rewindable even when
        // one of its parts is not. Writing the envelope would consume that part.
        this is MultipartBody -> parts.all { it.body.isRewindable() }
        else -> true
    }

    override fun newBuilder() = OkHttpRequestInfoBuilder(originalRequest.newBuilder())
        .withInternalLogger(internalLogger)

    internal companion object {
        internal const val ERROR_PEEK_REQUEST_BODY = "Unable to peek request body."
    }
}

/**
 * Raised by [TruncatingSink] to stop a [RequestBody] from producing bytes that would only be
 * discarded. Caught by the peeking code, which turns what was captured into a truncated snapshot.
 */
private class TruncationLimitReached : RuntimeException() {

    // The four-argument Throwable constructor can disable stack traces directly, but Android only
    // exposes it from API 24. This exception is internal control flow, so avoid building a stack
    // trace while remaining compatible with the SDK's API 23 minimum.
    override fun fillInStackTrace(): Throwable = this
}

/**
 * A [Sink] that keeps the first [maxBytes] bytes written to it and then stops the writer, so that
 * peeking at a large payload neither allocates nor produces more than the requested maximum.
 */
@Suppress("UnsafeThirdPartyFunctionCall") // exceptions surface through RequestBody.writeTo callers
private class TruncatingSink(private val maxBytes: Long) : Sink {

    private val captured: Buffer = Buffer()

    private var isTruncated: Boolean = false

    override fun write(source: Buffer, byteCount: Long) {
        val remaining = (maxBytes - captured.size).coerceAtLeast(0L)
        val captureCount = byteCount.coerceAtMost(remaining)

        if (captureCount > 0L) {
            captured.write(source, captureCount)
        }
        if (captureCount < byteCount) {
            isTruncated = true
            // Serializing the rest would cost the caller time and memory for bytes that are thrown
            // away, so the writer is stopped here instead. Sink offers no other way to say "enough".
            @Suppress("ThrowingInternalException") // caught by the peeking code, never escapes it
            throw TruncationLimitReached()
        }
    }

    fun snapshot(contentType: String?): HttpBodySnapshot = HttpBodySnapshot(
        bytes = captured.readByteArray(),
        contentType = contentType,
        isTruncated = isTruncated
    )

    // This sink is method-local and owns no external resource; snapshot() drains its buffer.
    override fun flush() = Unit

    override fun close() = Unit

    override fun timeout(): Timeout = Timeout.NONE
}

/**
 * For internal usage only.
 *
 * [HttpRequestInfoBuilder] implementation for OkHttp requests.
 * Allows modifying request properties such as URL, headers, and tags.
 *
 * @param requestBuilder the OkHttp request builder to modify.
 */
@InternalApi
@Suppress("UnsafeThirdPartyFunctionCall") // OkHttp builder methods are safe
class OkHttpRequestInfoBuilder(
    private val requestBuilder: Request.Builder
) : HttpRequestInfoBuilder {

    private var internalLogger: InternalLogger = InternalLogger.UNBOUND

    internal fun withInternalLogger(internalLogger: InternalLogger) = apply {
        this.internalLogger = internalLogger
    }

    override fun setUrl(url: String) = apply { requestBuilder.url(url) }

    override fun addHeader(key: String, vararg values: String) = apply {
        values.forEach { value ->
            requestBuilder.addHeader(key, value)
        }
    }

    override fun setMethod(
        method: String,
        body: HttpRequestBody?
    ) = apply { requestBuilder.method(method, (body as? OkHttpRequestBody)?.body) }

    override fun removeHeader(key: String) = apply { requestBuilder.removeHeader(key) }

    override fun <T> addTag(type: Class<in T>, tag: T?) = apply { requestBuilder.tag(type, tag) }

    override fun build(): HttpRequestInfo = requestBuilder.build().toHttpRequestInfo(internalLogger)
}
