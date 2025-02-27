/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger

internal class BitmapCachesManager(
    private val resourcesLRUCache: Cache<String, ByteArray>,
    private val bitmapPool: BitmapPool,
    private val logger: InternalLogger
) {
    private var isResourcesCacheRegisteredForCallbacks: Boolean = false
    private var isBitmapPoolRegisteredForCallbacks: Boolean = false

    @MainThread
    internal fun registerCallbacks(applicationContext: Context) {
        registerResourceLruCacheForCallbacks(applicationContext)
        registerBitmapPoolForCallbacks(applicationContext)
    }

    @MainThread
    private fun registerResourceLruCacheForCallbacks(applicationContext: Context) {
        if (isResourcesCacheRegisteredForCallbacks) return

        if (resourcesLRUCache is ComponentCallbacks2) {
            applicationContext.registerComponentCallbacks(resourcesLRUCache)
            isResourcesCacheRegisteredForCallbacks = true
        } else {
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { Cache.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS }
            )
        }
    }

    @MainThread
    private fun registerBitmapPoolForCallbacks(applicationContext: Context) {
        if (isBitmapPoolRegisteredForCallbacks) return

        applicationContext.registerComponentCallbacks(bitmapPool)
        isBitmapPoolRegisteredForCallbacks = true
    }

    internal fun putInResourceCache(key: String, resourceId: String) {
        resourcesLRUCache.put(key, resourceId.toByteArray(Charsets.UTF_8))
    }

    internal fun getFromResourceCache(key: String): String? {
        val resourceId = resourcesLRUCache.get(key) ?: return null
        return String(resourceId, Charsets.UTF_8)
    }

    internal fun generateResourceKeyFromDrawable(drawable: Drawable): String? {
        // TODO RUM-7740 - Handle unsafe cast
        return (resourcesLRUCache as? ResourcesLRUCache)?.generateKeyFromDrawable(drawable)
    }

    internal fun putInBitmapPool(bitmap: Bitmap) {
        bitmapPool.put(bitmap)
    }

    internal fun getBitmapByProperties(
        width: Int,
        height: Int,
        config: Bitmap.Config
    ): Bitmap? {
        return bitmapPool.getBitmapByProperties(width, height, config)
    }
}
