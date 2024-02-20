/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

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
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.async.NoopDataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class ResourcesSerializer private constructor(
    private val bitmapCachesManager: BitmapCachesManager,
    private val threadPoolExecutor: ExecutorService,
    private val drawableUtils: DrawableUtils,
    private val webPImageCompression: ImageCompression,
    private val logger: InternalLogger,
    private val md5HashGenerator: MD5HashGenerator,
    private val recordedDataQueueHandler: DataQueueHandler,
    private val applicationId: String
) {
    // resource IDs previously sent in this session -
    // optimization to avoid sending the same resource multiple times
    // atm this set is unbounded but expected to use relatively little space (~80kb per 1k items)
    private val resourceIdsSeen: MutableSet<String> =
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
        resourcesSerializerCallback: ResourcesSerializerCallback
    ) {
        bitmapCachesManager.registerCallbacks(applicationContext)

        val resourceId = tryToGetResourceFromCache(
            drawable = drawable,
            imageWireframe = imageWireframe,
            resourcesSerializerCallback = resourcesSerializerCallback
        )

        // managed to get resource from cache
        if (resourceId != null) {
            return
        }

        val bitmapFromDrawable = if (
            drawable is BitmapDrawable &&
            shouldUseDrawableBitmap(drawable)
        ) {
            drawable.bitmap // cannot be null - we already checked in shouldUseDrawableBitmap
        } else {
        null
        }

        // do in the background
        Runnable {
            createBitmapAsync(
                drawable = drawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                bitmapFromDrawable = bitmapFromDrawable,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback
            )
        }.let {
            threadPoolExecutor.executeSafe("handleBitmap", logger, it)
        }
    }

    // endregion

    // region testing

    @VisibleForTesting
    internal fun getThreadPoolExecutor(): ExecutorService = threadPoolExecutor

    // endregion

    // region private

    @WorkerThread
    private fun createBitmapAsync(
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        bitmapFromDrawable: Bitmap?,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ) {
        var handledBitmap: Bitmap? = null
        if (bitmapFromDrawable != null) {
            handledBitmap = tryToGetBitmapFromBitmapDrawable(
                drawable = drawable as BitmapDrawable,
                bitmapFromDrawable = bitmapFromDrawable,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback
            )
        }

        if (handledBitmap == null) {
            tryToDrawNewBitmap(
                drawable = drawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback
            )
        }
    }

    @Suppress("ReturnCount")
    @WorkerThread
    private fun serializeBitmap(
        drawable: Drawable,
        bitmap: Bitmap,
        byteArray: ByteArray,
        shouldCacheBitmap: Boolean,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ) {
        if (byteArray.isEmpty()) {
            // failed to get image data
            resourcesSerializerCallback.onReady()
            return
        }

        val resourceId = md5HashGenerator.generate(byteArray)

        if (shouldCacheBitmap) {
            bitmapCachesManager.putInBitmapPool(bitmap)
        }

        if (resourceId == null) {
            // resourceId is mandatory for resource endpoint
            resourcesSerializerCallback.onReady()
            return
        }

        if (!resourceIdsSeen.contains(resourceId)) {
            resourceIdsSeen.add(resourceId)

            // We probably don't want this here. In the next pr we'll
            // refactor this class and extract logic
            recordedDataQueueHandler.addResourceItem(
                identifier = resourceId,
                resourceData = byteArray,
                applicationId = applicationId
            )
        }

        bitmapCachesManager.putInResourceCache(drawable, resourceId)

        finalizeRecordedDataItem(resourceId, imageWireframe)
        resourcesSerializerCallback.onReady()
    }

    @WorkerThread
    private fun tryToDrawNewBitmap(
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ) {
        drawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = drawable,
            drawableWidth = drawableWidth,
            drawableHeight = drawableHeight,
            displayMetrics = displayMetrics,
            bitmapCreationCallback = object : BitmapCreationCallback {
                override fun onReady(bitmap: Bitmap) {
                    val byteArray = webPImageCompression.compressBitmap(bitmap)

                    @Suppress("ThreadSafety") // this runs inside an executor
                    serializeBitmap(
                        drawable = drawable,
                        bitmap = bitmap,
                        byteArray = byteArray,
                        shouldCacheBitmap = true,
                        imageWireframe = imageWireframe,
                        resourcesSerializerCallback = resourcesSerializerCallback
                    )
                }

                override fun onFailure() {
                    resourcesSerializerCallback.onReady()
                }
            }
        )
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun tryToGetBitmapFromBitmapDrawable(
        drawable: BitmapDrawable,
        bitmapFromDrawable: Bitmap,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ): Bitmap? {
        @Suppress("ThreadSafety") // this runs inside an executor
        drawableUtils.createScaledBitmap(bitmapFromDrawable)?.let { scaledBitmap ->

            /**
             * Check whether the scaled bitmap is the same as the original.
             * Since Bitmap.createScaledBitmap will return the original bitmap if the
             * requested dimensions match the dimensions of the original
             */
            val shouldCacheBitmap = scaledBitmap != drawable.bitmap

            val byteArray = webPImageCompression.compressBitmap(scaledBitmap)

            // failed to get byteArray potentially because the bitmap was recycled before imageCompression
            if (byteArray.isEmpty() && scaledBitmap.isRecycled) {
                return null
            }

            serializeBitmap(
                drawable = drawable,
                bitmap = scaledBitmap,
                byteArray = byteArray,
                shouldCacheBitmap = shouldCacheBitmap,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback
            )

            return scaledBitmap
        }

        return null
    }

    private fun tryToGetResourceFromCache(
        drawable: Drawable,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ): String? {
        val resourceId = bitmapCachesManager.getFromResourceCache(drawable)
            ?: return null

        finalizeRecordedDataItem(resourceId, imageWireframe)

        resourcesSerializerCallback.onReady()

        return resourceId
    }

    private fun finalizeRecordedDataItem(
        resourceId: String?,
        wireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        if (resourceId != null) {
            wireframe.resourceId = resourceId
            wireframe.isEmpty = false
        }
    }

    private fun shouldUseDrawableBitmap(drawable: BitmapDrawable): Boolean {
        return drawable.bitmap != null &&
                !drawable.bitmap.isRecycled &&
                drawable.bitmap.width > 0 &&
                drawable.bitmap.height > 0
    }

    // endregion

    // region builder
    internal class Builder(
        private var applicationId: String,
        private var recordedDataQueueHandler: DataQueueHandler = NoopDataQueueHandler(),
        private var logger: InternalLogger = InternalLogger.UNBOUND,
        private var threadPoolExecutor: ExecutorService = THREADPOOL_EXECUTOR,
        private var bitmapPool: BitmapPool,
        private var resourcesLRUCache: Cache<Drawable, CacheData>,
        private var bitmapCachesManager: BitmapCachesManager =
            BitmapCachesManager.Builder(
                resourcesLRUCache,
                bitmapPool,
                logger
            ).build(),
        private var drawableUtils: DrawableUtils = DrawableUtils(bitmapCachesManager = bitmapCachesManager),
        private var webPImageCompression: ImageCompression = WebPImageCompression(),
        private var md5HashGenerator: MD5HashGenerator = MD5HashGenerator(logger)
    ) {
        internal fun build() =
            ResourcesSerializer(
                logger = logger,
                threadPoolExecutor = threadPoolExecutor,
                drawableUtils = drawableUtils,
                webPImageCompression = webPImageCompression,
                md5HashGenerator = md5HashGenerator,
                recordedDataQueueHandler = recordedDataQueueHandler,
                applicationId = applicationId,
                bitmapCachesManager = bitmapCachesManager
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
