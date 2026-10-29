/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import com.datadog.android.internal.system.BuildSdkVersionProvider
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Reuses recorder-owned source-image pixels while their input and rendering parameters are unchanged.
 * Called only on the UI thread. Keys contain immutable rendering parameters, never UI objects.
 * Neither the original bitmap nor the copied pixels are retained, mutated or recycled by this cache.
 */
internal class ComposeImageBitmapCache<K>(
    maxEntries: Int = MAX_ENTRIES,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) {
    private data class State(
        val generation: Int,
        val width: Int,
        val height: Int,
        val config: Bitmap.Config?,
        val alpha: Boolean,
        val premultiplied: Boolean,
        val density: Int,
        val colorSpace: Any?
    )

    private data class Entry<K>(val state: State, val key: K, val pixels: WeakReference<Bitmap>)

    private val entries = WeakHashMap<Bitmap, Entry<K>>()

    private val maxEntries = maxEntries.coerceAtLeast(1)

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Guard bitmap metadata and an owned weak map.
    fun getOrCreate(source: Bitmap, key: K, create: () -> Bitmap?): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        val state = stateOf(source)
        val cached = entries[source]
        if (cached?.state == state && cached.key == key) {
            cached.pixels.get()?.takeUnless { it.isRecycled }?.let { return it }
        }
        val pixels = create() ?: return null
        // An app can update source pixels from another thread. Do not memoize a copy across that change.
        if (!source.isRecycled && stateOf(source) == state && !pixels.isRecycled) {
            entries.remove(source)
            if (entries.size >= maxEntries) {
                val iterator = entries.keys.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            entries[source] = Entry(state, key, WeakReference(pixels))
        }
        return pixels
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Metadata reads follow the recycled-bitmap guard on the UI thread.
    private fun stateOf(bitmap: Bitmap) = State(
        bitmap.generationId,
        bitmap.width,
        bitmap.height,
        bitmap.config,
        bitmap.hasAlpha(),
        bitmap.isPremultiplied,
        bitmap.density,
        if (buildSdkVersionProvider.isAtLeastO) bitmap.colorSpace else null
    )

    private companion object {
        const val MAX_ENTRIES = 64
    }
}
