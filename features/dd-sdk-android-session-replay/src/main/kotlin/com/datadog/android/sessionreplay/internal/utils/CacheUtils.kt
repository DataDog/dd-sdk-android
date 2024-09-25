/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.ComponentCallbacks2
import androidx.collection.LruCache

internal class CacheUtils<K : Any, V : Any>(
    private val invocationUtils: InvocationUtils = InvocationUtils()
) {

    // some of this memory level are not being triggered after API 34. We still need to keep them for now
    // for lower versions
    @Suppress("DEPRECATION")
    internal fun handleTrimMemory(level: Int, cache: LruCache<K, V>) {
        @Suppress("MagicNumber")
        val onLowMemorySizeBytes = cache.maxSize() / 2 // 50%

        @Suppress("MagicNumber")
        val onModerateMemorySizeBytes = (cache.maxSize() / 4) * 3 // 75%

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                evictAll(cache)
            }

            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                evictAll(cache)
            }

            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                trimToSize(cache, onModerateMemorySizeBytes)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                evictAll(cache)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                trimToSize(cache, onLowMemorySizeBytes)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                trimToSize(cache, onModerateMemorySizeBytes)
            }

            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {}

            else -> {
                evictAll(cache)
            }
        }
    }

    private fun evictAll(cache: LruCache<K, V>) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            call = { cache.evictAll() },
            failureMessage = FAILURE_MSG_EVICT_CACHE_CONTENTS
        )
    }

    private fun trimToSize(cache: LruCache<K, V>, targetSize: Int) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            call = { cache.trimToSize(targetSize) },
            failureMessage = FAILURE_MSG_TRIM_CACHE
        )
    }

    private companion object {
        private const val FAILURE_MSG_EVICT_CACHE_CONTENTS = "Failed to evict cache entries"
        private const val FAILURE_MSG_TRIM_CACHE = "Failed to trim cache to size"
    }
}
