/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.sessionreplay.internal.recorder.base64.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class Base64Serializer private constructor(
    private val threadPoolExecutor: ExecutorService,
    private val drawableUtils: DrawableUtils,
    private val base64Utils: Base64Utils,
    private val webPImageCompression: ImageCompression,
    private val base64LRUCache: Cache<Drawable, String>?,
    private val bitmapPool: BitmapPool?,
    private val logger: InternalLogger
) {
    private var isCacheRegisteredForCallbacks: Boolean = false
    private var isBitmapPoolRegisteredForCallbacks: Boolean = false

    // region internal

    @MainThread
    internal fun handleBitmap(
        applicationContext: Context,
        displayMetrics: DisplayMetrics,
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback
    ) {
        registerCallbacks(applicationContext)

        tryToGetBase64FromCache(drawable, imageWireframe, base64SerializerCallback)
            ?: tryToGetBitmapFromBitmapDrawable(
                drawable = drawable,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                base64SerializerCallback = base64SerializerCallback
            )
            ?: tryToDrawNewBitmap(
                drawable = drawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                base64SerializerCallback = base64SerializerCallback,

                // this parameter is used to avoid infinite recursion
                // basically we only allow one attempt to recreate the bitmap
                didCallOriginateFromFailover = false
            )
    }

    // endregion

    // region testing

    @VisibleForTesting
    internal fun getThreadPoolExecutor(): ExecutorService = threadPoolExecutor

    // endregion

    // region private

    @WorkerThread
    private fun serializeBitmap(
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        bitmap: Bitmap,
        shouldCacheBitmap: Boolean,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback,

        // this parameter is used to avoid infinite recursion
        // basically we only allow one attempt to recreate the bitmap
        didCallOriginateFromFailover: Boolean
    ) {
        val base64String = convertBitmapToBase64(
            drawable = drawable,
            displayMetrics = displayMetrics,
            bitmap = bitmap,
            shouldCacheBitmap = shouldCacheBitmap,
            imageWireframe = imageWireframe,
            base64SerializerCallback = base64SerializerCallback,
            didCallOriginateFromFailover = didCallOriginateFromFailover
        )

        if (base64String.isNotEmpty()) {
            finalizeRecordedDataItem(base64String, imageWireframe, base64SerializerCallback)
        }
    }

    @MainThread
    private fun registerCacheForCallbacks(applicationContext: Context) {
        if (isCacheRegisteredForCallbacks) return

        if (base64LRUCache is ComponentCallbacks2) {
            applicationContext.registerComponentCallbacks(base64LRUCache)
            isCacheRegisteredForCallbacks = true
        } else {
            // Temporarily use UNBOUND logger
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            logger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS }
            )
        }
    }

    @MainThread
    private fun registerBitmapPoolForCallbacks(applicationContext: Context) {
        if (isBitmapPoolRegisteredForCallbacks) return

        applicationContext.registerComponentCallbacks(bitmapPool)
        isBitmapPoolRegisteredForCallbacks = true
    }

    @WorkerThread
    private fun convertBitmapToBase64(
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        bitmap: Bitmap,
        shouldCacheBitmap: Boolean,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback,

        // this parameter is used to avoid infinite recursion
        // basically we only allow one attempt to recreate the bitmap
        didCallOriginateFromFailover: Boolean
    ): String {
        val base64Result: String

        val byteArray = webPImageCompression.compressBitmap(bitmap)

        // failed to get byteArray
        // Try once to recreate bitmap from the drawable
        if (byteArray.isEmpty() && bitmap.isRecycled && !didCallOriginateFromFailover) {
            tryToDrawNewBitmap(
                drawable = drawable,
                drawableWidth = bitmap.width,
                drawableHeight = bitmap.height,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                base64SerializerCallback = base64SerializerCallback,
                didCallOriginateFromFailover = true
            )

            return ""
        }

        base64Result = base64Utils.serializeToBase64String(byteArray)

        if (base64Result.isNotEmpty()) {
            // if we got a base64 string then cache it
            base64LRUCache?.put(drawable, base64Result)
        }

        if (shouldCacheBitmap) {
            bitmapPool?.put(bitmap)
        }

        return base64Result
    }

    private fun tryToDrawNewBitmap(
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback,
        didCallOriginateFromFailover: Boolean
    ) {
        drawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = drawable,
            drawableWidth = drawableWidth,
            drawableHeight = drawableHeight,
            displayMetrics = displayMetrics,
            base64SerializerCallback = base64SerializerCallback,
            bitmapCreationCallback = object : BitmapCreationCallback {
                override fun onReady(bitmap: Bitmap) {
                    Runnable {
                        @Suppress("ThreadSafety") // this runs inside an executor
                        serializeBitmap(
                            drawable = drawable,
                            displayMetrics = displayMetrics,
                            bitmap = bitmap,
                            shouldCacheBitmap = true,
                            imageWireframe = imageWireframe,
                            base64SerializerCallback = base64SerializerCallback,
                            didCallOriginateFromFailover = didCallOriginateFromFailover
                        )
                    }.let {
                        threadPoolExecutor.executeSafe("tryToDrawNewBitmap", logger, it)
                    }
                }
            }
        )
    }

    @MainThread
    private fun tryToGetBitmapFromBitmapDrawable(
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback
    ): Bitmap? {
        if (drawable is BitmapDrawable && shouldUseDrawableBitmap(drawable)) {
            val bitmap = drawable.bitmap // cannot be null - we already checked in shouldUseDrawableBitmap
            Runnable {
                @Suppress("ThreadSafety") // this runs inside an executor
                drawableUtils.createScaledBitmap(
                    bitmap
                )?.let { scaledBitmap ->

                    /**
                     * Check whether the scaled bitmap is the same as the original.
                     * Since Bitmap.createScaledBitmap will return the original bitmap if the
                     * requested dimensions match the dimensions of the original
                     */
                    val shouldCacheBitmap = scaledBitmap != drawable.bitmap

                    serializeBitmap(
                        drawable = drawable,
                        displayMetrics = displayMetrics,
                        bitmap = scaledBitmap,
                        shouldCacheBitmap = shouldCacheBitmap,
                        imageWireframe = imageWireframe,
                        base64SerializerCallback = base64SerializerCallback,
                        didCallOriginateFromFailover = false
                    )
                }
            }.let {
                threadPoolExecutor.executeSafe("tryToGetBitmapFromBitmapDrawable", logger, it)
            }

            // return a value to indicate that we are handling the bitmap
            return bitmap
        }

        return null
    }

    private fun tryToGetBase64FromCache(
        drawable: Drawable,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback
    ): String? {
        return base64LRUCache?.get(drawable)?.let { base64String ->
            finalizeRecordedDataItem(base64String, imageWireframe, base64SerializerCallback)
            base64String
        }
    }

    private fun finalizeRecordedDataItem(
        base64String: String,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        base64SerializerCallback: Base64SerializerCallback
    ) {
        if (base64String.isNotEmpty()) {
            wireframe.base64 = base64String
            wireframe.isEmpty = false
        }

        base64SerializerCallback.onReady()
    }

    private fun shouldUseDrawableBitmap(drawable: BitmapDrawable): Boolean {
        return drawable.bitmap != null &&
            !drawable.bitmap.isRecycled &&
            drawable.bitmap.width > 0 &&
            drawable.bitmap.height > 0
    }

    @MainThread
    private fun registerCallbacks(applicationContext: Context) {
        registerCacheForCallbacks(applicationContext)
        registerBitmapPoolForCallbacks(applicationContext)
    }

    // endregion

    // region builder
    internal class Builder(
        // Temporarily use UNBOUND until we handle the loggers
        private var logger: InternalLogger = InternalLogger.UNBOUND,
        private var threadPoolExecutor: ExecutorService = THREADPOOL_EXECUTOR,
        private var bitmapPool: BitmapPool? = null,
        private var base64LRUCache: Cache<Drawable, String>? = null,
        private var drawableUtils: DrawableUtils = DrawableUtils(
            bitmapPool = bitmapPool,
            threadPoolExecutor = threadPoolExecutor,
            logger = logger
        ),
        private var base64Utils: Base64Utils = Base64Utils(),
        private var webPImageCompression: ImageCompression = WebPImageCompression()
    ) {
        internal fun build() =
            Base64Serializer(
                logger = logger,
                threadPoolExecutor = threadPoolExecutor,
                bitmapPool = bitmapPool,
                base64LRUCache = base64LRUCache,
                drawableUtils = drawableUtils,
                base64Utils = base64Utils,
                webPImageCompression = webPImageCompression
            )

        private companion object {
            private const val THREAD_POOL_MAX_KEEP_ALIVE_MS = 5000L
            private const val CORE_DEFAULT_POOL_SIZE = 1
            private const val MAX_THREAD_COUNT = 10

            @Suppress("UnsafeThirdPartyFunctionCall") // all parameters are non-negative and queue is not null
            private val THREADPOOL_EXECUTOR = ThreadPoolExecutor(
                CORE_DEFAULT_POOL_SIZE,
                MAX_THREAD_COUNT,
                THREAD_POOL_MAX_KEEP_ALIVE_MS,
                TimeUnit.MILLISECONDS,
                LinkedBlockingDeque()
            )
        }
    }

    internal interface BitmapCreationCallback {
        fun onReady(bitmap: Bitmap)
    }

    // endregion
}
