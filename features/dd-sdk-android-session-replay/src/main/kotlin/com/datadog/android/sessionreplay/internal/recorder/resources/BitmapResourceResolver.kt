/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService

/** Shares unchanged images and owns only the snapshots accepted by the recorder's budget. */
internal class BitmapResourceResolver(
    private val executorService: ExecutorService,
    private val logger: InternalLogger,
    private val pending: PendingResourceRequests,
    private val resolveBitmap: (Bitmap) -> String?
) {
    private val lock = Any()
    private val completed = WeakHashMap<Bitmap, CompletedResource>()

    @MainThread
    fun resolve(bitmap: Bitmap, callback: ResourceResolverCallback) {
        if (!isValid(bitmap) || callback.cancellation?.isCancelled == true) {
            callback.onFailure()
            return
        }
        val version = BitmapVersion(bitmap.generationId, bitmap.width, bitmap.height, bitmap.config)
        val cached = synchronized(lock) { completed[bitmap]?.takeIf { it.version == version } }
        if (cached != null) {
            callback.onSuccess(cached.resourceId)
        } else {
            resolveUncached(bitmap, version, callback)
        }
    }

    private fun isValid(bitmap: Bitmap): Boolean = !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0

    @MainThread
    private fun resolveUncached(bitmap: Bitmap, version: BitmapVersion, callback: ResourceResolverCallback) {
        if (!pending.canAllocate) {
            callback.onFailure()
            return
        }
        val pixels = version.width.toLong() * version.height
        if (pixels > PendingResourceRequests.MAX_PENDING_BYTES / ARGB_BYTES_PER_PIXEL) {
            callback.onFailure()
            return
        }
        // ALPHA_8 conversion temporarily owns both its snapshot and an ARGB_8888 bitmap.
        val alphaBytes = if (version.config == Bitmap.Config.ALPHA_8) {
            bitmap.allocationByteCount.toLong().coerceAtLeast(pixels)
        } else {
            0L
        }
        val key = BitmapRequestKey(bitmap, version)
        pending.resolve(key, pixels * ARGB_BYTES_PER_PIXEL + alphaBytes, callback) { request ->
            if (request.cancellation.isCancelled) {
                request.onFailure()
            } else {
                copyAndSchedule(bitmap, key, request)
            }
        }
    }

    @MainThread
    private fun copyAndSchedule(bitmap: Bitmap, key: BitmapRequestKey, callback: ResourceResolverCallback) {
        val config = if (key.version.config == Bitmap.Config.ALPHA_8) {
            Bitmap.Config.ALPHA_8
        } else {
            Bitmap.Config.ARGB_8888
        }
        val snapshot = try {
            copyBitmap(bitmap, config)
        } catch (e: OutOfMemoryError) {
            pending.onOutOfMemory(e, callback)
            return
        }
        if (snapshot == null) {
            callback.onFailure()
            return
        }
        ResourceProcessingTask(
            callback.cancellation,
            onCancelled = {
                snapshot.recycle()
                callback.onFailure()
            },
            onFailure = { callback.onFailure() },
            action = { process(snapshot, key, callback) }
        ).execute(executorService, logger)
    }

    private fun copyBitmap(bitmap: Bitmap, config: Bitmap.Config): Bitmap? = try {
        @Suppress("UnsafeThirdPartyFunctionCall") // Recycled sources and allocation failure are handled below.
        bitmap.copy(config, false)
    } catch (e: IllegalStateException) {
        logCopyFailure(e)
        null
    }

    private fun logCopyFailure(error: Throwable) {
        logger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Unable to copy a Session Replay bitmap" },
            error,
            onlyOnce = true
        )
    }

    private fun process(bitmap: Bitmap, key: BitmapRequestKey, callback: ResourceResolverCallback) {
        try {
            val resourceId = try {
                if (pending.canAllocate) {
                    resolveBitmap(bitmap)?.also { cache(key, it) }
                } else {
                    null
                }
            } finally {
                bitmap.recycle()
            }
            if (resourceId == null) {
                callback.onFailure()
            } else {
                callback.onSuccess(resourceId)
            }
        } catch (e: OutOfMemoryError) {
            pending.onOutOfMemory(e, callback)
        }
    }

    private fun cache(key: BitmapRequestKey, resourceId: String) = synchronized(lock) {
        val source = key.source.get() ?: return@synchronized
        if (completed.size >= MAX_CACHED_RESOURCES && completed[source] == null) {
            val iterator = completed.keys.iterator()
            if (iterator.hasNext()) {
                @Suppress("UnsafeThirdPartyFunctionCall") // hasNext retains the key; lock excludes mutations.
                iterator.next()
                @Suppress("UnsafeThirdPartyFunctionCall") // Mutable map iterator; next was just called.
                iterator.remove()
            }
        }
        completed[source] = CompletedResource(key.version, resourceId)
    }

    private data class BitmapVersion(
        val generationId: Int,
        val width: Int,
        val height: Int,
        val config: Bitmap.Config?
    )

    private class BitmapRequestKey(bitmap: Bitmap, val version: BitmapVersion) {
        @Suppress("UnsafeThirdPartyFunctionCall") // The source is non-null and is retained weakly.
        val source = WeakReference(bitmap)
        private val sourceHash = System.identityHashCode(bitmap)

        override fun hashCode(): Int = 31 * sourceHash + version.hashCode()

        override fun equals(other: Any?): Boolean = this === other ||
            (
                other is BitmapRequestKey && version == other.version &&
                    source.get()?.let { it === other.source.get() } == true
                )
    }

    private class CompletedResource(val version: BitmapVersion, val resourceId: String)

    internal companion object {
        private const val ARGB_BYTES_PER_PIXEL = 4
        internal const val MAX_CACHED_RESOURCES = 256
    }
}
