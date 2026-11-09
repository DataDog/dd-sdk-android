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
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResourceKey
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResources
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Weak, bounded entry/capture metadata. Encoded resource presence stays owned by the raw cache. */
internal class DefaultCompositionTextResources(
    private val rawContentCache: LruCache<Long, String>,
    private val maxEntries: Int = MAX_ENTRIES,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : CompositionTextResources {
    private data class PixelState(
        val generation: Int,
        val width: Int,
        val height: Int,
        val alpha: Boolean,
        val premultiplied: Boolean,
        val density: Int,
        val colorSpace: Any?
    )

    private class ResourceEntry(var access: Long, var signature: Long? = null)
    private class CaptureBinding(
        val key: WeakReference<CompositionTextResourceKey>,
        val entry: ResourceEntry,
        val state: PixelState
    )

    private val lock = Any()
    private val entries = WeakHashMap<CompositionTextResourceKey, ResourceEntry>()

    // A tombstone prevents an ambiguously shared/mutated bitmap from publishing either binding.
    private val captures = WeakHashMap<Bitmap, CaptureBinding?>()
    private var access = 0L
    private var stopped = false

    // Keep authoritative cache access outside the metadata lock.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun findResourceId(key: CompositionTextResourceKey): String? {
        val entry = synchronized(lock) {
            if (stopped) return null
            entries[key]?.also { it.access = ++access }
        } ?: return null
        val signature = synchronized(lock) { entry.signature } ?: return null
        val resourceId = rawContentCache.get(signature)
        return synchronized(lock) {
            if (stopped || entries[key] !== entry || entry.signature != signature) null else resourceId
        }
    }

    // Bounded weak maps never own glyph pixels or layout objects.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun registerCapture(key: CompositionTextResourceKey, bitmap: Bitmap) {
        val state = stateOf(bitmap) ?: return
        synchronized(lock) {
            if (stopped || maxEntries <= 0) return
            if (captures.containsKey(bitmap)) {
                invalidateAmbiguousBinding(key, bitmap, state)
                return
            }
            if (captures.size >= maxEntries) return
            if (!entries.containsKey(key) && entries.size >= maxEntries) {
                entries.entries.minByOrNull { it.value.access }?.let { entries.remove(it.key) }
            }
            // Weak keys can disappear during eviction; an unavailable bookkeeping slot is a cache miss.
            if (!entries.containsKey(key) && entries.size >= maxEntries) return
            val entry = ResourceEntry(++access)
            entries[key] = entry
            captures[bitmap] = CaptureBinding(WeakReference(key), entry, state)
        }
    }

    // Called under lock. Re-registering the same immutable output is idempotent; conflicting reuse is a miss.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun invalidateAmbiguousBinding(key: CompositionTextResourceKey, bitmap: Bitmap, state: PixelState) {
        val previous = captures[bitmap]
        if (previous?.key?.get() === key && previous.state == state && entries[key] === previous.entry) return
        previous?.key?.get()?.let { previousKey ->
            if (entries[previousKey] === previous.entry) entries.remove(previousKey)
        }
        entries.remove(key)
        captures[bitmap] = null
    }

    /** Called only after ordinary text-only pixel processing resolved this exact bitmap successfully. */
    // A replaced entry or changed output cannot receive a late signature.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    fun onTextResolved(bitmap: Bitmap, signature: Long) {
        val state = stateOf(bitmap) ?: return
        synchronized(lock) {
            if (stopped) return
            val binding = captures[bitmap] ?: return
            val key = binding.key.get() ?: return
            if (binding.state == state && entries[key] === binding.entry) binding.entry.signature = signature
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            entries.clear()
            captures.clear()
        }
    }

    // Only recorder-owned ARGB glyph output qualifies.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall", "NewApi")
    private fun stateOf(bitmap: Bitmap): PixelState? {
        if (bitmap.isRecycled || bitmap.config != Bitmap.Config.ARGB_8888) return null
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()) return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        return PixelState(
            bitmap.generationId,
            width,
            height,
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
