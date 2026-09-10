/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

import androidx.annotation.WorkerThread

/**
 * This interface indicates that the payload of a request or a response can be read without
 * consuming it.
 *
 * It is implemented by the [HttpRequestInfo] and [HttpResponseInfo] instances of the networking
 * libraries that support peeking. Rather than checking for this interface, call the
 * [HttpRequestInfo.peekBody] and [HttpResponseInfo.peekBody] extensions, which return null when
 * the underlying library does not support it.
 */
interface PeekableBodyInfo {

    /**
     * Reads the beginning of the payload without consuming it, so that a request stays intact and
     * can still be sent, and a response stays fully readable by the application.
     *
     * @param maxBytes the maximum number of bytes to capture, capped at
     * [HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES]. Anything beyond that is dropped and the returned
     * snapshot is flagged as [HttpBodySnapshot.isTruncated].
     * @return a snapshot of the payload, or null when there is no payload, or it couldn't be read.
     */
    @WorkerThread
    fun peekBody(maxBytes: Long): HttpBodySnapshot?
}

/**
 * Reads the beginning of the request payload without consuming it, so that the request stays
 * intact and can still be sent.
 *
 * The payload is copied into memory on top of what the networking library already holds. Producing
 * it may require real work, such as reading a file or running a serializer. Only call this when the
 * payload is actually needed. Nothing is read until this method is called.
 *
 * @param maxBytes the maximum number of bytes to capture, capped at
 * [HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES]. Anything beyond that is dropped and the returned
 * snapshot is flagged as [HttpBodySnapshot.isTruncated]. A value of zero or less captures nothing
 * and returns null.
 * @return a snapshot of the payload. Returns null when the request has no payload, or it couldn't
 * be read. One-shot and duplex bodies also return null because they can only be transmitted once.
 * A networking library that does not support peeking returns null as well.
 */
@WorkerThread
fun HttpRequestInfo.peekBody(
    maxBytes: Long = HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES
): HttpBodySnapshot? {
    // bound to the interface type on purpose: calling peekBody() on `this` would resolve back to
    // this very extension and recurse
    val peekable = this as? PeekableBodyInfo ?: return null
    return peekable.peekBody(maxBytes.coerceAtMost(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES))
}

/**
 * Reads the beginning of the response payload without consuming it, so that the payload stays
 * fully readable by the application.
 *
 * This function is intended only for finite, non-streaming response payloads. Networking-library
 * implementations return null for streaming types they can identify, such as Server-Sent Events,
 * gRPC, and WebSocket responses.
 *
 * The payload is copied into memory on top of what the networking library already holds. Reading
 * it may block until enough of the payload has arrived, so only call this when it is actually
 * needed.
 *
 * The byte limit bounds memory use, not read duration. A streaming response that is missing or
 * misreporting its type may block until the networking library's read timeout. Nothing is read
 * until this method is called.
 *
 * @param maxBytes the maximum number of bytes to capture, capped at
 * [HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES]. Anything beyond that is dropped and the returned
 * snapshot is flagged as [HttpBodySnapshot.isTruncated]. A value of zero or less captures nothing
 * and returns null.
 * @return a snapshot of the payload. Returns null when the response has no payload, or it couldn't
 * be read. Streams that do not complete on their own also return null. These include Server-Sent
 * Events, gRPC, and WebSocket responses. A networking library that does not support peeking returns
 * null as well.
 */
@WorkerThread
fun HttpResponseInfo.peekBody(
    maxBytes: Long = HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES
): HttpBodySnapshot? {
    // bound to the interface type on purpose: calling peekBody() on `this` would resolve back to
    // this very extension and recurse
    val peekable = this as? PeekableBodyInfo ?: return null
    return peekable.peekBody(maxBytes.coerceAtMost(HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES))
}
