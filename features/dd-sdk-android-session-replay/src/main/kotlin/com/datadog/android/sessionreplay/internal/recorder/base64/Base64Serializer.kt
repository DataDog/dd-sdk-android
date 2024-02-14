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
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.RESOURCE_ENDPOINT_FEATURE_FLAG
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.async.NoopDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.base64.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Collections
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
    private val base64LRUCache: Cache<Drawable, CacheData>,
    private val bitmapPool: BitmapPool?,
    private val logger: InternalLogger,
    private val md5HashGenerator: MD5HashGenerator,
    private val recordedDataQueueHandler: DataQueueHandler,
    private val applicationId: String
) {
    private var isBase64CacheRegisteredForCallbacks: Boolean = false
    private var isBitmapPoolRegisteredForCallbacks: Boolean = false

    // resources previously sent in this session -
    // optimization to avoid sending the same resource multiple times
    private val previouslySentResources: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

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

        tryToGetBase64FromCache(
            drawable = drawable,
            imageWireframe = imageWireframe,
            base64SerializerCallback = base64SerializerCallback
        )
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

    @Suppress("ReturnCount")
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
        val byteArray = webPImageCompression.compressBitmap(bitmap)

        // failed to get byteArray potentially because the bitmap was recycled before imageCompression
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

            return
        }

        if (byteArray.isEmpty()) {
            // failed to get image data
            base64SerializerCallback.onReady()
            return
        }

        val resourceId = md5HashGenerator.generate(byteArray)
        var base64String = "".toByteArray(Charsets.UTF_8)
        var cacheData = CacheData(base64String, resourceId?.toByteArray(Charsets.UTF_8))

        if (RESOURCE_ENDPOINT_FEATURE_FLAG) {
            if (resourceId == null) {
                // resourceId is mandatory for resource endpoint
                base64SerializerCallback.onReady()
                return
            }

            if (!previouslySentResources.contains(resourceId)) {
                previouslySentResources.add(resourceId)
                recordedDataQueueHandler.addResourceItem(
                    identifier = resourceId,
                    resourceData = byteArray,
                    applicationId = applicationId
                )
            }
        } else {
            base64String = convertBitmapToBase64(
                byteArray = byteArray,
                bitmap = bitmap,
                shouldCacheBitmap = shouldCacheBitmap
            ).toByteArray(Charsets.UTF_8)
            cacheData = CacheData(base64String, resourceId?.toByteArray(Charsets.UTF_8))
        }

        if (base64String.isNotEmpty() || resourceId != null) {
            base64LRUCache.put(drawable, cacheData)
        }

        finalizeRecordedDataItem(cacheData, imageWireframe)
        base64SerializerCallback.onReady()
    }

    @MainThread
    private fun registerBase64LruCacheForCallbacks(applicationContext: Context) {
        if (isBase64CacheRegisteredForCallbacks) return

        if (base64LRUCache is ComponentCallbacks2) {
            applicationContext.registerComponentCallbacks(base64LRUCache)
            isBase64CacheRegisteredForCallbacks = true
        } else {
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
        byteArray: ByteArray,
        bitmap: Bitmap,
        shouldCacheBitmap: Boolean
    ): String {
        val base64Result = base64Utils.serializeToBase64String(byteArray)

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

                override fun onFailure() {
                    base64SerializerCallback.onReady()
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
        val cacheData = base64LRUCache.get(drawable)

        if (cacheData?.resourceId == null) {
            return null
        }

        if (cacheData.base64Encoding.isNotEmpty()) {
            finalizeRecordedDataItem(cacheData, imageWireframe)
        }

        base64SerializerCallback.onReady()

        return String(cacheData.base64Encoding, Charsets.UTF_8)
    }

    private fun finalizeRecordedDataItem(
        cacheData: CacheData,
        wireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        val base64 = String(cacheData.base64Encoding, Charsets.UTF_8)

        val resourceId = cacheData.resourceId?.let {
            String(it, Charsets.UTF_8)
        }

        if (resourceId != null) {
            wireframe.resourceId = resourceId
            wireframe.isEmpty = false
        }

        if (base64.isNotEmpty()) {
            wireframe.base64 = base64
            wireframe.isEmpty = false
        }
    }

    private fun shouldUseDrawableBitmap(drawable: BitmapDrawable): Boolean {
        return drawable.bitmap != null &&
            !drawable.bitmap.isRecycled &&
            drawable.bitmap.width > 0 &&
            drawable.bitmap.height > 0
    }

    @MainThread
    private fun registerCallbacks(applicationContext: Context) {
        registerBase64LruCacheForCallbacks(applicationContext)
        registerBitmapPoolForCallbacks(applicationContext)
    }

    // endregion

    // region builder
    internal class Builder(
        private var applicationId: String,
        private var recordedDataQueueHandler: DataQueueHandler = NoopDataQueueHandler(),
        private var logger: InternalLogger = InternalLogger.UNBOUND,
        private var threadPoolExecutor: ExecutorService = THREADPOOL_EXECUTOR,
        private var bitmapPool: BitmapPool,
        private var base64LRUCache: Cache<Drawable, CacheData>,
        private var drawableUtils: DrawableUtils = DrawableUtils(
            bitmapPool = bitmapPool,
            threadPoolExecutor = threadPoolExecutor,
            logger = logger
        ),
        private var base64Utils: Base64Utils = Base64Utils(),
        private var webPImageCompression: ImageCompression = WebPImageCompression(),
        private var md5HashGenerator: MD5HashGenerator = MD5HashGenerator(logger)
    ) {
        internal fun build() =
            Base64Serializer(
                logger = logger,
                threadPoolExecutor = threadPoolExecutor,
                bitmapPool = bitmapPool,
                base64LRUCache = base64LRUCache,
                drawableUtils = drawableUtils,
                base64Utils = base64Utils,
                webPImageCompression = webPImageCompression,
                md5HashGenerator = md5HashGenerator,
                recordedDataQueueHandler = recordedDataQueueHandler,
                applicationId = applicationId
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
        fun onFailure()
    }

    // endregion
}
