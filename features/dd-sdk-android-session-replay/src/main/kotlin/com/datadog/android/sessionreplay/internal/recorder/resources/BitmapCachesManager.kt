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
import com.datadog.android.lint.InternalApi

/**
 * Manages the Bitmap caches used by the Session Replay feature.
 */
@InternalApi
class BitmapCachesManager {

    /**
     * Creates a new [BitmapCachesManager] instance.
     * @param logger the logger used to log internal logs
     */
    constructor(
        logger: InternalLogger
    ) : this(ResourcesLRUCache(), BitmapPool(), InternalLogger.UNBOUND)

    internal constructor(
        resourcesLRUCache: Cache<Drawable, ByteArray>,
        bitmapPool: BitmapPool,
        logger: InternalLogger
    ) {
        this.resourcesLRUCache = resourcesLRUCache
        this.bitmapPool = bitmapPool
        this.logger = logger
    }

    private val resourcesLRUCache: Cache<Drawable, ByteArray>
    private val bitmapPool: BitmapPool
    private val logger: InternalLogger
    private var isResourcesCacheRegisteredForCallbacks: Boolean = false
    private var isBitmapPoolRegisteredForCallbacks: Boolean = false

    /**
     * Registers the caches for the [ComponentCallbacks2] callbacks.
     * @param applicationContext the application context
     */
    @MainThread
    fun registerCallbacks(applicationContext: Context) {
        registerResourceLruCacheForCallbacks(applicationContext)
        registerBitmapPoolForCallbacks(applicationContext)
    }

    /**
     * Puts a resourceId in the resources cache.
     * @param key the key to use to store the resourceId
     * @param resourceId the resourceId to store
     */
    fun putInResourceCache(key: String, resourceId: String) {
        resourcesLRUCache.put(key, resourceId.toByteArray(Charsets.UTF_8))
    }

    /**
     * Gets a resourceId from the resources cache.
     * @param key the key to use to retrieve the resourceId
     * @return the resourceId or null if not found
     */
    fun getFromResourceCache(key: String): String? {
        val resourceId = resourcesLRUCache.get(key) ?: return null
        return String(resourceId, Charsets.UTF_8)
    }

    /**
     * Generates a key from a drawable.
     * @param drawable the drawable to generate the key from
     * @return the generated key or null if unsuccessful
     */
    fun generateResourceKeyFromDrawable(drawable: Drawable): String? {
        return (resourcesLRUCache as? ResourcesLRUCache)?.generateKeyFromDrawable(drawable)
    }

    /**
     * Puts a bitmap in the bitmap pool.
     * @param bitmap the bitmap to put in the pool
     */
    fun putInBitmapPool(bitmap: Bitmap) {
        bitmapPool.put(bitmap)
    }

    /**
     * Gets a bitmap from the bitmap pool by the properties of the bitmap.
     * @param width the width of the bitmap
     * @param height the height of the bitmap
     * @param config the config of the bitmap
     * @return the bitmap or null if not found
     */
    fun getBitmapByProperties(
        width: Int,
        height: Int,
        config: Bitmap.Config
    ): Bitmap? {
        return bitmapPool.getBitmapByProperties(width, height, config)
    }

    @MainThread
    private fun registerBitmapPoolForCallbacks(applicationContext: Context) {
        if (isBitmapPoolRegisteredForCallbacks) return

        applicationContext.registerComponentCallbacks(bitmapPool)
        isBitmapPoolRegisteredForCallbacks = true
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
}
