/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.annotation.MainThread
import com.datadog.android.internal.system.BuildSdkVersionProvider

/**
 * Tracks the image captures waiting to be resolved. A capture of an image that is already being resolved waits for that
 * work instead of repeating it, and new work is refused while the pixels of pending work exceed a budget.
 */
internal class PendingImageRequests(
    buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
    maxHeapBytes: Long = Runtime.getRuntime().maxMemory()
) {
    // Before API 26 bitmap pixels count against the application heap, which can be smaller than the default budget.
    private val maxPendingBytes = if (buildSdkVersionProvider.isAtLeastO) {
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
    fun start(key: String?, byteCount: Long, callback: ResourceResolverCallback): Request? {
        var isRefused = false
        val request = synchronized(lock) {
            val existing = key?.let { inFlight[it] }
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
                    if (key != null) inFlight[key] = request
                    pendingBytes += byteCount
                }
            }
        }
        if (isRefused) callback.onFailure()
        return request
    }

    /** Completes every capture waiting on this request and releases its budget, once, whichever path ends it. */
    internal inner class Request internal constructor(
        private val key: String?,
        private val byteCount: Long,
        callback: ResourceResolverCallback
    ) : ResourceResolverCallback {
        // Guarded by the enclosing lock.
        private val callbacks = mutableListOf(callback)
        private var isCompleted = false

        override fun onSuccess(resourceId: String) {
            complete().forEach { it.onSuccess(resourceId) }
        }

        override fun onFailure() {
            complete().forEach { it.onFailure() }
        }

        internal fun join(callback: ResourceResolverCallback) {
            @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned mutable list.
            callbacks.add(callback)
        }

        // Callbacks run after the lock is released, so their work never blocks captures starting on the main thread.
        private fun complete(): List<ResourceResolverCallback> = synchronized(lock) {
            if (isCompleted) return emptyList()
            isCompleted = true
            pendingBytes -= byteCount
            // While this request is in flight no other request can be registered under its key.
            if (key != null) inFlight.remove(key)
            callbacks.toList()
        }
    }

    internal companion object {
        internal const val MAX_PENDING_BYTES = 64L * 1024 * 1024
        internal const val HEAP_FRACTION = 4
    }
}
