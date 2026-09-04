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
     * @return a snapshot of the payload, or null when there is no payload or it couldn't be read.
     */
    @WorkerThread
    fun peekBody(maxBytes: Long): HttpBodySnapshot?
}

/**
 * Reads the beginning of the request payload without consuming it, so that the request stays
 * intact and can still be sent.
 *
 * The payload is copied into memory on top of what the networking library already holds, and
 * producing it may require real work (reading a file, running a serializer), so only call this when
 * the payload is actually needed. Nothing is read until this method is called.
 *
 * @param maxBytes the maximum number of bytes to capture, capped at
 * [HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES]. Anything beyond that is dropped and the returned
 * snapshot is flagged as [HttpBodySnapshot.isTruncated]. A value of zero or less captures nothing
 * and returns null.
 * @return a snapshot of the payload, or null when the request has no payload, when it can only be
 * transmitted once (a one-shot or duplex body), when it couldn't be read, or when the networking
 * library behind this request does not support peeking.
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
 * The payload is copied into memory on top of what the networking library already holds, and
 * reading it may block until enough of it has arrived, so only call this when the payload is
 * actually needed. Nothing is read until this method is called.
 *
 * @param maxBytes the maximum number of bytes to capture, capped at
 * [HttpBodySnapshot.DEFAULT_MAX_BODY_BYTES]. Anything beyond that is dropped and the returned
 * snapshot is flagged as [HttpBodySnapshot.isTruncated]. A value of zero or less captures nothing
 * and returns null.
 * @return a snapshot of the payload, or null when the response has no payload, when it is a stream
 * that would never complete on its own (Server-Sent Events, gRPC, WebSocket), when it couldn't be
 * read, or when the networking library behind this response does not support peeking.
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
