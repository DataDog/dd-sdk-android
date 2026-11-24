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
 * Tracks the image captures waiting to be resolved. A capture that makes the same image the same way as queued work
 * waits for that work instead of repeating it: one of a bitmap, which is copied when the capture is admitted, until the
 * work completes, and one of a drawable, which the worker reads when it starts, only until then, so that it never shows
 * an image older than its own snapshot.
 *
 * A capture reserves the pixels of the bitmap copy it keeps alive while it waits, and new work is refused while the
 * reservations of pending work exceed a budget. A capture of a drawable that is not attached to a view is held until it
 * is processed, so no more than [MAX_HELD_DRAWABLES] are held at once; their pixels belong to the application and are
 * not counted, so they are limited by number. A drawable attached to a view is held weakly and reserves nothing. Captures are processed one at a time, so what processing allocates, such as a raster or a scaled
 * copy, is not reserved.
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
    private val joinable: MutableMap<CaptureKey, Request> = mutableMapOf()
    private var pendingBytes = 0L
    private var heldDrawables = 0

    /**
     * Starts resolving a capture, or attaches it to the request already resolving the same [key].
     * @return the request to resolve, or null when the capture joined in-flight work or was refused; a refused
     * capture's [callback] has already failed.
     */
    @MainThread
    fun start(key: CaptureKey, byteCount: Long, callback: ResourceResolverCallback): Request? {
        var isRefused = false
        val request = synchronized(lock) {
            val existing = joinable[key]
            when {
                existing != null -> {
                    existing.join(callback)
                    null
                }

                isOverBudget(key, byteCount) -> {
                    isRefused = true
                    null
                }

                else -> Request(key, byteCount, callback).also { request ->
                    joinable[key] = request
                    pendingBytes += byteCount
                    if (key.holdsDrawableStrongly) heldDrawables++
                }
            }
        }
        if (isRefused) deliver { callback.onFailure() }
        return request
    }

    // A capture is always admitted while no other pending work holds pixels, so an image larger than the budget can
    // still be captured. Called with the lock held.
    private fun isOverBudget(key: CaptureKey, byteCount: Long): Boolean {
        val isOverBytes = byteCount > 0 && pendingBytes > 0 && pendingBytes + byteCount > maxPendingBytes
        val isOverHeld = key.holdsDrawableStrongly && heldDrawables >= MAX_HELD_DRAWABLES
        return isOverBytes || isOverHeld
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
        private val key: CaptureKey,
        private val byteCount: Long,
        callback: ResourceResolverCallback
    ) : ResourceResolverCallback {
        // Guarded by the enclosing lock.
        private val callbacks = mutableListOf(callback)
        private var isCompleted = false

        /** Called when the worker is about to read the image of a drawable, after which no capture joins it. */
        internal fun begin() {
            synchronized(lock) { leaveJoinable() }
        }

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
            if (key.holdsDrawableStrongly) heldDrawables--
            leaveJoinable()
            callbacks.toList()
        }

        // Once this request stopped being joinable another one with an equal key can have taken its place.
        private fun leaveJoinable() {
            if (joinable[key] === this) joinable.remove(key)
        }
    }

    internal companion object {
        internal const val MAX_PENDING_BYTES = 64L * 1024 * 1024
        internal const val HEAP_FRACTION = 4
        internal const val MAX_HELD_DRAWABLES = 64
        internal const val CAPTURE_CALLBACK_FAILED = "A Session Replay image capture callback threw an exception"
    }
}
