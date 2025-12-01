/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.collection.LruCache

internal interface Alpha8ResourceCache : ComponentCallbacks2 {
    /**
     * Generates a cache key for the bitmap. This is separated from get/put so the caller
     * can compute the key once and reuse it for both operations.
     * @return The cache key, or null if signature generation failed
     */
    fun generateKey(bitmap: Bitmap): Alpha8CacheKey?

    fun get(key: Alpha8CacheKey): String?

    fun put(key: Alpha8CacheKey, resourceId: String)
}

internal data class Alpha8CacheKey(
    val width: Int,
    val height: Int,
    val signature: Long
)

internal class DefaultAlpha8ResourceCache(
    private val signatureGenerator: BitmapSignatureGenerator,
    private val cache: LruCache<Alpha8CacheKey, ByteArray> =
        object : LruCache<Alpha8CacheKey, ByteArray>(MAX_CACHE_MEMORY_SIZE_BYTES) {
            override fun sizeOf(key: Alpha8CacheKey, value: ByteArray): Int {
                return value.size
            }
        }
) : Alpha8ResourceCache {

    override fun generateKey(bitmap: Bitmap): Alpha8CacheKey? {
        val signature = signatureGenerator.generateSignature(bitmap) ?: return null
        return Alpha8CacheKey(bitmap.width, bitmap.height, signature)
    }

    override fun get(key: Alpha8CacheKey): String? {
        return cache[key]?.toString(Charsets.UTF_8)
    }

    override fun put(key: Alpha8CacheKey, resourceId: String) {
        cache.put(key, resourceId.toByteArray(Charsets.UTF_8))
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        @Suppress("MagicNumber")
        val onLowMemorySizeBytes = cache.maxSize() / 2

        @Suppress("MagicNumber")
        val onModerateMemorySizeBytes = (cache.maxSize() / 4) * 3

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.evictAll()
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                cache.trimToSize(onModerateMemorySizeBytes)
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                cache.trimToSize(onLowMemorySizeBytes)
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {}
            else -> {
                cache.evictAll()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        cache.evictAll()
    }

    internal companion object {
        const val MAX_CACHE_MEMORY_SIZE_BYTES = 4 * 1024 * 1024 // 4MB
    }
}
