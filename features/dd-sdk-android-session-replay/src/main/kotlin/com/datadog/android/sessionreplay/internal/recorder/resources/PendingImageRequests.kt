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
 * Tracks the image captures waiting to be resolved and bounds the pixels they hold.
 *
 * Each capture reserves the most bitmap pixels the SDK allocates or keeps alive for it at once, from admission until it
 * completes: its copy or scaled copy, a full readback or conversion together with the copy scaled from it or with the
 * temporary a hardware bitmap is first read back into, and the buffer a wrapped hardware bitmap keeps alive.
 * Reservations are estimated from the image being captured, and one is raised under the same rule as admission when the
 * drawable copied on the worker holds a bitmap that needs more, such as one a custom DrawableCopier returns or one set
 * on the drawable since. Working memory of the one capture being processed, such as compression buffers, is not
 * counted.
 *
 * New work is refused while the reservations of pending captures would exceed the budget, but a capture with nothing
 * else pending is always admitted, so that a large image can still be captured. Its largest single allocation is
 * bounded instead: a full-size readback, conversion or copy is skipped when it alone would exceed the budget, and any
 * other copy or raster is at most the 10 MB drawing limit.
 *
 * A capture of an image that is already being resolved waits for that work instead of repeating it, and each of the
 * captures waiting on that work receives its result even when another one's callback throws.
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

                // A request alone is always admitted, so an image larger than the budget can still be captured.
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
        if (isRefused) callback.onFailure()
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
        byteCount: Long,
        callback: ResourceResolverCallback
    ) : ResourceResolverCallback {
        // Guarded by the enclosing lock.
        private var byteCount = byteCount
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

        /**
         * Raises this request's reservation to [requiredByteCount] when the budget allows it, under the same rule as
         * admission.
         * @return whether the request now reserves at least [requiredByteCount]
         */
        internal fun reserve(requiredByteCount: Long): Boolean = synchronized(lock) {
            val otherPendingBytes = pendingBytes - byteCount
            when {
                isCompleted -> false
                requiredByteCount <= byteCount -> true
                otherPendingBytes > 0 && otherPendingBytes + requiredByteCount > maxPendingBytes -> false
                else -> {
                    pendingBytes = otherPendingBytes + requiredByteCount
                    byteCount = requiredByteCount
                    true
                }
            }
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
