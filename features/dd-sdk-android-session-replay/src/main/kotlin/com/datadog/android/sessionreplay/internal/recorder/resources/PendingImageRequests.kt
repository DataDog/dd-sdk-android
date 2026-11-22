/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider

/**
 * Tracks the image captures waiting to be resolved. A capture of an image that is already being resolved waits for that
 * work instead of repeating it, and new work is refused while the pixels of pending work exceed a budget.
 *
 * A capture reserves the pixels it keeps alive while it waits: its copy of a bitmap, or the bitmaps a detached drawable
 * holds. A drawable attached to a view is held weakly and reserves nothing. Captures are processed one at a time, so
 * what processing allocates, such as a raster or a scaled copy, is not reserved.
 */
internal class PendingImageRequests(
    private val internalLogger: InternalLogger,
    buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
    maxHeapBytes: Long = Runtime.getRuntime().maxMemory()
) {
    // Before API 26 bitmap pixels count against the application heap, which can be smaller than the default budget.
    internal val maxPendingBytes = if (buildSdkVersionProvider.isAtLeastO) {
        MAX_PENDING_BYTES
    } else {
        minOf(MAX_PENDING_BYTES, maxHeapBytes / HEAP_FRACTION)
    }
    private val lock = Any()
    private val inFlight: MutableMap<String, Request> = mutableMapOf()
    private var pendingBytes = 0L

    /**
     * Starts resolving a capture, or attaches it to the request already resolving the same [key].
     * @return the request to resolve, or null when the capture joined in-flight work or was refused; a refused
     * capture's [callback] has already failed.
     */
    @MainThread
    fun start(key: String, byteCount: Long, callback: ResourceResolverCallback): Request? {
        var isRefused = false
        val request = synchronized(lock) {
            val existing = inFlight[key]
            when {
                existing != null -> {
                    existing.join(callback)
                    null
                }

                // A request is always admitted while no other pending work holds pixels, so an image larger than the
                // budget can still be captured.
                pendingBytes > 0 && pendingBytes + byteCount > maxPendingBytes -> {
                    isRefused = true
                    null
                }

                else -> Request(key, byteCount, callback).also { request ->
                    inFlight[key] = request
                    pendingBytes += byteCount
                }
            }
        }
        if (isRefused) deliver { callback.onFailure() }
        return request
    }

    // A capture's callback can run application code, such as a custom mapper's job status callback.
    @Suppress("TooGenericExceptionCaught")
    private fun deliver(result: () -> Unit) {
        try {
            result()
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { CAPTURE_CALLBACK_FAILED },
                e,
                onlyOnce = true
            )
        }
    }

    /** Completes every capture waiting on this request and releases its budget, once, whichever path ends it. */
    internal inner class Request internal constructor(
        private val key: String,
        private val byteCount: Long,
        callback: ResourceResolverCallback
    ) : ResourceResolverCallback {
        // Guarded by the enclosing lock.
        private val callbacks = mutableListOf(callback)
        private var isCompleted = false

        override fun onSuccess(resourceId: String) {
            complete().forEach { callback -> deliver { callback.onSuccess(resourceId) } }
        }

        override fun onFailure() {
            complete().forEach { callback -> deliver { callback.onFailure() } }
        }

        internal fun join(callback: ResourceResolverCallback) {
            callbacks.add(callback)
        }

        // Callbacks run after the lock is released, so their work never blocks captures starting on the main thread.
        private fun complete(): List<ResourceResolverCallback> = synchronized(lock) {
            if (isCompleted) return emptyList()
            isCompleted = true
            pendingBytes -= byteCount
            // While this request is in flight no other request can be registered under its key.
            inFlight.remove(key)
            callbacks.toList()
        }
    }

    internal companion object {
        internal const val MAX_PENDING_BYTES = 64L * 1024 * 1024
        internal const val HEAP_FRACTION = 4
        internal const val CAPTURE_CALLBACK_FAILED = "A Session Replay image capture callback threw an exception"
    }
}
