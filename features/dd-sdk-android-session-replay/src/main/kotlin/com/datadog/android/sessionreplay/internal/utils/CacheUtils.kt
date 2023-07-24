/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.ComponentCallbacks2
import android.util.LruCache

internal class CacheUtils<K : Any, V : Any> {
    internal fun handleTrimMemory(level: Int, cache: LruCache<K, V>) {
        @Suppress("MagicNumber")
        val onLowMemorySizeBytes = cache.maxSize() / 2 // 50%

        @Suppress("MagicNumber")
        val onModerateMemorySizeBytes = (cache.maxSize() / 4) * 3 // 75%

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                cache.evictAll()
            }

            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                cache.evictAll()
            }

            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                cache.trimToSize(onModerateMemorySizeBytes)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                cache.evictAll()
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                cache.trimToSize(onLowMemorySizeBytes)
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                cache.trimToSize(onModerateMemorySizeBytes)
            }

            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {}

            else -> {
                cache.evictAll()
            }
        }
    }
}
