/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.internal.time.TimeProvider

/**
 * Coalesces requests before image allocation, with bounded pixels and waiting snapshots.
 * Allocation failures pause new image work briefly so updating screens cannot retry continuously.
 */
internal class PendingResourceRequests(
    private val logger: InternalLogger,
    private val timeProvider: TimeProvider = DefaultTimeProvider()
) {
    private val lock = Any()
    private val pending = mutableMapOf<Any, Request>()
    private var reservedBytes = 0L
    private var lastAllocationFailureNs = 0L
    private var hasAllocationFailed = false

    val canAllocate: Boolean
        get() = synchronized(lock) {
            !hasAllocationFailed ||
                timeProvider.getDeviceElapsedTimeNanos() - lastAllocationFailureNs >= ALLOCATION_BACKOFF_NS
        }

    fun onOutOfMemory(error: OutOfMemoryError, callback: ResourceResolverCallback) {
        synchronized(lock) {
            lastAllocationFailureNs = timeProvider.getDeviceElapsedTimeNanos()
            hasAllocationFailed = true
        }
        callback.onFailure()
        logger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Pausing Session Replay image allocations for 5 seconds after an allocation failure" },
            error,
            onlyOnce = true
        )
    }

    fun resolve(key: Any, byteCount: Long, callback: ResourceResolverCallback, load: (Request) -> Unit) {
        if (!canAllocate) {
            callback.onFailure()
            return
        }
        val entry = CallbackEntry(callback)
        var isNew = false
        val request = synchronized(lock) {
            val existing = pending[key]
            if (existing != null) {
                if (existing.isAbandoned || existing.callbacks.size >= MAX_CALLBACKS_PER_RESOURCE) {
                    null
                } else {
                    @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned mutable list.
                    existing.callbacks.add(entry)
                    existing
                }
            } else if (byteCount <= 0 || byteCount > MAX_PENDING_BYTES - reservedBytes ||
                pending.size >= MAX_PENDING_REQUESTS
            ) {
                null
            } else {
                Request(key, byteCount).also { request ->
                    @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned mutable list.
                    request.callbacks.add(entry)
                    pending[key] = request
                    reservedBytes += byteCount
                    isNew = true
                }
            }
        }
        if (request == null) {
            logger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { "Skipping Session Replay image capture because the pending bitmap budget is full" },
                onlyOnce = true
            )
            callback.onFailure()
            return
        }
        val unregister = callback.cancellation?.register { cancel(request, entry) }
        synchronized(lock) {
            @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned list of identity keys; access is serialized.
            if (request.callbacks.contains(entry)) {
                entry.unregister = unregister
            } else {
                unregister?.invoke()
            }
        }
        if (isNew) load(request)
    }

    private fun cancel(request: Request, entry: CallbackEntry) {
        val abandoned = synchronized(lock) {
            @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned mutable list of identity keys.
            if (!request.callbacks.remove(entry)) return
            entry.unregister?.invoke()
            entry.unregister = null
            val isAbandoned = request.callbacks.isEmpty()
            request.isAbandoned = isAbandoned
            isAbandoned
        }
        if (abandoned) request.cancellation.cancel()
        entry.callback.onFailure()
    }

    private fun complete(request: Request, resourceId: String?) {
        val entries = synchronized(lock) {
            if (pending[request.key] !== request) return
            @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned map; request keys have non-throwing equality.
            pending.remove(request.key)
            reservedBytes -= request.byteCount
            request.callbacks.toList().also { request.callbacks.clear() }
        }
        entries.forEach { entry ->
            entry.unregister?.invoke()
            if (resourceId == null) {
                entry.callback.onFailure()
            } else {
                entry.callback.onSuccess(resourceId)
            }
        }
    }

    internal inner class Request internal constructor(
        internal val key: Any,
        internal val byteCount: Long
    ) : ResourceResolverCallback {
        internal val callbacks = mutableListOf<CallbackEntry>()
        internal var isAbandoned = false
        override val cancellation = ResourceCancellation()

        override fun onSuccess(resourceId: String) = complete(this, resourceId)

        override fun onFailure() = complete(this, null)
    }

    internal class CallbackEntry(val callback: ResourceResolverCallback) {
        var unregister: (() -> Unit)? = null
    }

    internal companion object {
        internal const val ALLOCATION_BACKOFF_NS = 5_000_000_000L
        internal const val MAX_PENDING_BYTES = 64L * 1024 * 1024
        internal const val MAX_PENDING_REQUESTS = 32
        internal const val MAX_CALLBACKS_PER_RESOURCE = 32
    }
}
