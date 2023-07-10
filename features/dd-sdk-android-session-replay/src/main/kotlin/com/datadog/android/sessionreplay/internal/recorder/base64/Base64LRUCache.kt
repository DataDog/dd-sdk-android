/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.LayerDrawable
import android.util.LruCache
import androidx.annotation.VisibleForTesting

internal object Base64LRUCache : Cache<Drawable, String>, ComponentCallbacks2 {
    @Suppress("MagicNumber")
    val MAX_CACHE_MEMORY_SIZE_BYTES = 4 * 1024 * 1024 // 4MB
    @Suppress("MagicNumber")
    private val ON_LOW_MEMORY_SIZE_BYTES = MAX_CACHE_MEMORY_SIZE_BYTES / 2 // 50% size
    @Suppress("MagicNumber")
    private val ON_MODERATE_MEMORY_SIZE_BYTES = (MAX_CACHE_MEMORY_SIZE_BYTES / 4) * 3 // 75% size

    private var cache: LruCache<String, ByteArray> = object :
        LruCache<String, ByteArray>(MAX_CACHE_MEMORY_SIZE_BYTES) {
        override fun sizeOf(key: String?, value: ByteArray): Int {
            return value.size
        }
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                cache.evictAll()
            }

            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cache.evictAll()
            }

            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                cache.trimToSize(ON_MODERATE_MEMORY_SIZE_BYTES)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.evictAll()
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                cache.trimToSize(ON_LOW_MEMORY_SIZE_BYTES)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                cache.trimToSize(ON_MODERATE_MEMORY_SIZE_BYTES)
            }

            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                cache.evictAll()
            }

            else -> {
                cache.evictAll()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        cache.evictAll()
    }

    @VisibleForTesting
    internal fun setBackingCache(cache: LruCache<String, ByteArray>) {
        this.cache = cache
    }

    @Synchronized
    override fun put(element: Drawable, value: String) {
        val key = generateKey(element)
        val byteArray = value.toByteArray(Charsets.UTF_8)
        cache.put(key, byteArray)
    }

    @Synchronized
    override fun get(element: Drawable): String? =
        cache.get(generateKey(element))?.let {
            String(it)
        }

    @Synchronized
    override fun size(): Int = cache.size()

    @Synchronized
    override fun clear() {
        cache.evictAll()
    }

    private fun generateKey(drawable: Drawable): String =
        generatePrefix(drawable) + System.identityHashCode(drawable)

    private fun generatePrefix(drawable: Drawable): String {
        return when (drawable) {
            is DrawableContainer -> getPrefixForDrawableContainer(drawable)
            is LayerDrawable -> getPrefixForLayerDrawable(drawable)
            else -> ""
        }
    }

    private fun getPrefixForDrawableContainer(drawable: DrawableContainer): String {
        if (drawable !is AnimationDrawable) {
            return drawable.state.joinToString(separator = "", postfix = "-")
        }

        return ""
    }

    private fun getPrefixForLayerDrawable(drawable: LayerDrawable): String {
        return if (drawable.numberOfLayers > 1) {
            val sb = StringBuilder()
            for (index in 0 until drawable.numberOfLayers) {
                val layer = drawable.getDrawable(index)
                val layerHash = System.identityHashCode(layer).toString()
                sb.append(layerHash)
                sb.append("-")
            }
            "$sb"
        } else {
            ""
        }
    }
}
