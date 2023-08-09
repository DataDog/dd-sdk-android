/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Build
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.utils.CacheUtils
import com.datadog.android.sessionreplay.internal.utils.InvocationUtils
import java.util.concurrent.atomic.AtomicInteger

@Suppress("TooManyFunctions")
internal object BitmapPool : Cache<String, Bitmap>, ComponentCallbacks2 {
    private const val BITMAP_OPERATION_FAILED = "operation failed for bitmap pool"

    @VisibleForTesting
    @Suppress("MagicNumber")
    internal val MAX_CACHE_MEMORY_SIZE_BYTES = 4 * 1024 * 1024 // 4MB

    private var bitmapsBySize = HashMap<String, HashSet<Bitmap>>()
    private var usedBitmaps = HashSet<Bitmap>()
    private val logger = InternalLogger.UNBOUND
    private val invocationUtils = InvocationUtils()
    private var bitmapIndex = AtomicInteger(0)

    private var cache: LruCache<String, Bitmap> = object :
        LruCache<String, Bitmap>(MAX_CACHE_MEMORY_SIZE_BYTES) {
        override fun sizeOf(key: String?, bitmap: Bitmap): Int {
            return bitmap.allocationByteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Bitmap?,
            newValue: Bitmap?
        ) {
            super.entryRemoved(evicted, key, oldValue, newValue)

            if (oldValue != null) {
                val dimensionsKey = generateKey(oldValue)
                val bitmapGroup = bitmapsBySize[dimensionsKey] ?: HashSet()

                invocationUtils.safeCallWithErrorLogging(
                    logger = logger,
                    call = {
                        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                        bitmapGroup.remove(oldValue)
                    },
                    failureMessage = BITMAP_OPERATION_FAILED
                )
                markBitmapAsFree(oldValue)
                oldValue.recycle()
            }
        }
    }

    @VisibleForTesting
    internal fun setBitmapsBySize(bitmaps: HashMap<String, HashSet<Bitmap>>) {
        this.bitmapsBySize = bitmaps
    }

    @VisibleForTesting
    internal fun setBackingCache(cache: LruCache<String, Bitmap>) {
        this.cache = cache
    }

    @VisibleForTesting
    internal fun setUsedBitmaps(usedBitmaps: HashSet<Bitmap>) {
        this.usedBitmaps = usedBitmaps
    }

    @Synchronized
    override fun put(value: Bitmap) {
        // don't allow immutable or recycled bitmaps in the pool
        if (!value.isMutable || value.isRecycled) {
            return
        }

        val key = generateKey(value)

        val bitmapExistsInPool = invocationUtils.safeCallWithErrorLogging(
            logger = logger,
            call = {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                bitmapsBySize[key]?.contains(value) ?: false
            },
            failureMessage = BITMAP_OPERATION_FAILED
        ) ?: false

        if (!bitmapExistsInPool) {
            addBitmapToPool(key, value)
        }

        markBitmapAsFree(value)
    }

    override fun size(): Int = cache.size()

    @Synchronized
    override fun clear() = cache.evictAll()

    @Synchronized
    override fun get(element: String): Bitmap? {
        val bitmapsWithReqDimensions = bitmapsBySize[element] ?: return null

        // find the first unused bitmap, mark it as used and return it
        return bitmapsWithReqDimensions.find {
            invocationUtils.safeCallWithErrorLogging(
                logger = logger,
                call = { !usedBitmaps.contains(it) },
                failureMessage = BITMAP_OPERATION_FAILED
            ) ?: false
        }?.apply { markBitmapAsUsed(this) }
    }

    internal fun getBitmapByProperties(width: Int, height: Int, config: Config): Bitmap? {
        val key = generateKey(width, height, config)
        return get(key)
    }

    private fun markBitmapAsFree(bitmap: Bitmap) {
        invocationUtils.safeCallWithErrorLogging(
            logger = logger,
            call = {
                usedBitmaps.remove(bitmap)
            },
            failureMessage = BITMAP_OPERATION_FAILED
        )
    }

    private fun markBitmapAsUsed(bitmap: Bitmap) {
        invocationUtils.safeCallWithErrorLogging(
            logger = logger,
            call = {
                @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
                usedBitmaps.add(bitmap)
            },
            failureMessage = BITMAP_OPERATION_FAILED
        )
    }

    private fun addBitmapToPool(key: String, bitmap: Bitmap) {
        val cacheIndex = bitmapIndex.incrementAndGet()
        val cacheKey = "$key-$cacheIndex"
        cache.put(cacheKey, bitmap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
            invocationUtils.safeCallWithErrorLogging(
                logger = logger,
                call = { bitmapsBySize.putIfAbsent(key, HashSet()) },
                failureMessage = BITMAP_OPERATION_FAILED
            )
        } else {
            if (bitmapsBySize[key] == null) bitmapsBySize[key] = HashSet()
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
        invocationUtils.safeCallWithErrorLogging(
            logger = logger,
            call = { bitmapsBySize[key]?.add(bitmap) },
            failureMessage = BITMAP_OPERATION_FAILED
        )
    }

    private fun generateKey(bitmap: Bitmap) =
        generateKey(bitmap.width, bitmap.height, bitmap.config)

    private fun generateKey(width: Int, height: Int, config: Config) =
        "$width-$height-$config"

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        cache.evictAll()
    }

    override fun onTrimMemory(level: Int) {
        val cacheUtils = CacheUtils<String, Bitmap>()
        cacheUtils.handleTrimMemory(level, cache)
    }
}
