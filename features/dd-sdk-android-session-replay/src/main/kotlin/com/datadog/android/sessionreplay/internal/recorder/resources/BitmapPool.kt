/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import com.datadog.android.sessionreplay.internal.utils.CacheUtils
import java.util.concurrent.atomic.AtomicInteger

@Suppress("TooManyFunctions")
internal class BitmapPool(
    private val bitmapPoolHelper: BitmapPoolHelper = BitmapPoolHelper(),
    private val cacheUtils: CacheUtils<String, Bitmap> = CacheUtils(),
    @get:VisibleForTesting internal val bitmapsBySize: HashMap<String, HashSet<Bitmap>> = HashMap(),
    @get:VisibleForTesting internal val usedBitmaps: HashSet<Bitmap> = HashSet(),
    private var cache: LruCache<String, Bitmap> = object :
        LruCache<String, Bitmap>(MAX_CACHE_MEMORY_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount
        }

        @Synchronized
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            bitmapPoolHelper.safeCall {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                super.entryRemoved(evicted, key, oldValue, newValue)
            }

            val dimensionsKey = bitmapPoolHelper.generateKey(oldValue)
            val bitmapGroup = bitmapsBySize[dimensionsKey] ?: HashSet()

            bitmapPoolHelper.safeCall {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                bitmapGroup.remove(oldValue)
            }

            bitmapPoolHelper.safeCall {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                usedBitmaps.remove(oldValue)
            }

            oldValue.recycle()
        }
    }
) : Cache<String, Bitmap>, ComponentCallbacks2 {
    private var bitmapIndex = AtomicInteger(0)

    @Synchronized
    override fun put(value: Bitmap) {
        // don't allow immutable or recycled bitmaps in the pool
        if (!value.isMutable || value.isRecycled) {
            return
        }

        val key = bitmapPoolHelper.generateKey(value)

        val bitmapExistsInPool = bitmapPoolHelper.safeCall {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            bitmapsBySize[key]?.contains(value) ?: false
        } ?: false

        if (!bitmapExistsInPool) {
            addBitmapToPool(key, value)
        }

        markBitmapAsFree(value)
    }

    override fun size(): Int = cache.size()

    @Synchronized
    override fun clear() {
        bitmapPoolHelper.safeCall {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            cache.evictAll()
        }
    }

    @Synchronized
    override fun get(element: String): Bitmap? {
        val bitmapsWithReqDimensions = bitmapsBySize[element] ?: return null

        // find the first unused bitmap, mark it as used and return it
        return bitmapsWithReqDimensions.find {
            bitmapPoolHelper.safeCall {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                !usedBitmaps.contains(it)
            } ?: false
        }?.apply { markBitmapAsUsed(this) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    @Synchronized
    override fun onLowMemory() {
        bitmapPoolHelper.safeCall {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            cache.evictAll()
        }
    }

    @Synchronized
    override fun onTrimMemory(level: Int) {
        cacheUtils.handleTrimMemory(level, cache)
    }

    internal fun getBitmapByProperties(width: Int, height: Int, config: Config): Bitmap? {
        val key = bitmapPoolHelper.generateKey(width, height, config)
        return get(key)
    }

    private fun markBitmapAsFree(bitmap: Bitmap) {
        bitmapPoolHelper.safeCall {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            usedBitmaps.remove(bitmap)
        }
    }

    private fun markBitmapAsUsed(bitmap: Bitmap) {
        bitmapPoolHelper.safeCall {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            usedBitmaps.add(bitmap)
        }
    }

    private fun addBitmapToPool(key: String, bitmap: Bitmap) {
        val cacheIndex = bitmapIndex.incrementAndGet()
        val cacheKey = "$key-$cacheIndex"

        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        bitmapPoolHelper.safeCall {
            cache.put(cacheKey, bitmap)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bitmapPoolHelper.safeCall {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                bitmapsBySize.putIfAbsent(key, HashSet())
            }
        } else {
            if (bitmapsBySize[key] == null) bitmapsBySize[key] = HashSet()
        }

        bitmapPoolHelper.safeCall {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            bitmapsBySize[key]?.add(bitmap)
        }
    }

    internal companion object {
        @VisibleForTesting
        @Suppress("MagicNumber")
        internal val MAX_CACHE_MEMORY_SIZE_BYTES = 4 * 1024 * 1024 // 4MB
    }
}
