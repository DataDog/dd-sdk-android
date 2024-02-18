/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.LayerDrawable
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import com.datadog.android.sessionreplay.internal.recorder.safeGetDrawable
import com.datadog.android.sessionreplay.internal.utils.CacheUtils
import com.datadog.android.sessionreplay.internal.utils.InvocationUtils

internal class ResourcesLRUCache(
    private val cacheUtils: CacheUtils<String, CacheData> = CacheUtils(),
    private val invocationUtils: InvocationUtils = InvocationUtils(),
    private var cache: LruCache<String, CacheData> =
        object :
            LruCache<String, CacheData>(MAX_CACHE_MEMORY_SIZE_BYTES) {
            override fun sizeOf(key: String, value: CacheData): Int {
                return value.resourceId.size
            }
        }
) : Cache<Drawable, CacheData>, ComponentCallbacks2 {

    override fun onTrimMemory(level: Int) {
        cacheUtils.handleTrimMemory(level, cache)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            call = { cache.evictAll() },
            failureMessage = FAILURE_MSG_EVICT_CACHE_CONTENTS
        )
    }

    @Synchronized
    override fun put(element: Drawable, value: CacheData) {
        val key = generateKey(element)
        val resourceId = value.resourceId

        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            call = { cache.put(key, CacheData(resourceId)) },
            failureMessage = FAILURE_MSG_PUT_CACHE
        )
    }

    @Synchronized
    override fun get(element: Drawable): CacheData? =
        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            call = {
                cache.get(generateKey(element))?.let {
                    val resourceId = it.resourceId
                    CacheData(resourceId)
                }
            },
            failureMessage = FAILURE_MSG_GET_CACHE
        )

    override fun size(): Int = cache.size()

    @Synchronized
    override fun clear() {
        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            call = { cache.evictAll() },
            failureMessage = FAILURE_MSG_EVICT_CACHE_CONTENTS
        )
    }

    @VisibleForTesting
    internal fun generateKey(drawable: Drawable): String =
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
        val sb = StringBuilder()
        for (index in 0 until drawable.numberOfLayers) {
            val layer = drawable.safeGetDrawable(index)
            val layerHash = System.identityHashCode(layer).toString()
            sb.append(layerHash)
            sb.append("-")
        }
        return "$sb"
    }

    internal companion object {
        @Suppress("MagicNumber")
        internal val MAX_CACHE_MEMORY_SIZE_BYTES = 4 * 1024 * 1024 // 4MB

        private const val FAILURE_MSG_EVICT_CACHE_CONTENTS = "Failed to evict cache entries"
        private const val FAILURE_MSG_PUT_CACHE = "Failed to put item in cache"
        private const val FAILURE_MSG_GET_CACHE = "Failed to get item from cache"
    }
}

internal data class CacheData(val resourceId: ByteArray) {
    // we must override these methods because we are using arrays as properties
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CacheData

        return resourceId.contentEquals(other.resourceId)
    }

    override fun hashCode(): Int {
        return resourceId.contentHashCode()
    }
}
