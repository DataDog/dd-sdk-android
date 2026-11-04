/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.os.Build
import androidx.collection.LruCache
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.recorder.composition.CompositionSourceImageResources
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Weak, bounded metadata only. The processor's existing raw-content cache remains authoritative. */
internal class DefaultCompositionSourceImageResources(
    private val rawContentCache: LruCache<Long, String>,
    private val maxEntries: Int = MAX_ENTRIES,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : CompositionSourceImageResources {
    private data class SourceState(
        val generation: Int,
        val width: Int,
        val height: Int,
        val config: Bitmap.Config,
        val alpha: Boolean,
        val premultiplied: Boolean,
        val density: Int,
        val colorSpace: Any?
    )

    private class SourceEntry(val state: SourceState, var access: Long, var signature: Long? = null)
    private class CopyBinding(val source: WeakReference<Bitmap>, val state: SourceState)

    private val lock = Any()
    private val sources = WeakHashMap<Bitmap, SourceEntry>()

    // A null value marks an ambiguous copy. Keep the tombstone until its weak key is reclaimed.
    private val copies = WeakHashMap<Bitmap, CopyBinding?>()
    private var access = 0L
    private var stopped = false

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned weak maps and positive bounded caches.
    override fun findResourceId(source: Bitmap): String? {
        val state = stateOf(source) ?: return null
        val signature = synchronized(lock) {
            if (stopped) return null
            val previous = sources[source]
            val entry = if (previous?.state == state) {
                previous
            } else {
                sources.remove(source)
                if (sources.size >= maxEntries.coerceAtLeast(1)) {
                    sources.entries.minByOrNull { it.value.access }?.let { sources.remove(it.key) }
                }
                // Weak keys can disappear during eviction. Full bookkeeping must fall back to capture.
                if (sources.size >= maxEntries.coerceAtLeast(1)) return null
                SourceEntry(state, 0L).also { sources[source] = it }
            }
            entry.access = ++access
            entry.signature
        } ?: return null
        // Never hold our metadata lock while consulting the resource cache (or vice versa).
        val resourceId = rawContentCache.get(signature)
        return synchronized(lock) { if (stopped) null else resourceId }
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Read copied bitmap metadata and owned weak maps.
    override fun registerSourceCopy(source: Bitmap, copy: Bitmap) {
        val state = stateOf(source) ?: return
        if (copy.isRecycled || copy.config != Bitmap.Config.ARGB_8888) return
        if (copy.width != state.width || copy.height != state.height) return
        synchronized(lock) {
            if (stopped || sources[source]?.state != state) return
            val previous = copies[copy]
            if (copies.containsKey(copy)) {
                if (previous == null || previous.source.get() !== source || previous.state != state) copies[copy] = null
            } else if (copies.size < maxEntries.coerceAtLeast(1)) {
                copies[copy] = CopyBinding(WeakReference(source), state)
            }
        }
    }

    /** Only called for successfully resolved source-image captures, after the raw cache is populated. */
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Guard stopped/expired weak metadata without pixels.
    fun onSourceResolved(copy: Bitmap, signature: Long) {
        synchronized(lock) {
            if (stopped) return
            val binding = copies[copy] ?: return
            val source = binding.source.get() ?: return
            val entry = sources[source] ?: return
            if (entry.state == binding.state) entry.signature = signature
        }
    }

    /** Late callbacks from a disposed processor cannot repopulate its metadata. */
    fun stop() {
        synchronized(lock) {
            stopped = true
            sources.clear()
            copies.clear()
        }
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall", "NewApi") // API checks use the injected SDK version.
    private fun stateOf(bitmap: Bitmap): SourceState? {
        if (bitmap.isRecycled || bitmap.isMutable) return null
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()) {
            return null
        }
        val width = bitmap.width
        val height = bitmap.height
        val config = bitmap.config
        if (width <= 0 || height <= 0) return null
        if (config == null || config == Bitmap.Config.ALPHA_8) return null
        return SourceState(
            bitmap.generationId,
            width,
            height,
            config,
            bitmap.hasAlpha(),
            bitmap.isPremultiplied,
            bitmap.density,
            if (buildSdkVersionProvider.isAtLeastO) bitmap.colorSpace else null
        )
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
