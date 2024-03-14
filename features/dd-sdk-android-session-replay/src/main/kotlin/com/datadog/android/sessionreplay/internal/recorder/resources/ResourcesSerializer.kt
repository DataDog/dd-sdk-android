/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Resources
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
import com.datadog.android.sessionreplay.internal.recorder.resources.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class ResourcesSerializer private constructor(
    private val threadPoolExecutor: ExecutorService,
    private val drawableUtils: DrawableUtils,
    private val webPImageCompression: ImageCompression,
    private val resourcesLRUCache: Cache<Drawable, ByteArray>,
    private val bitmapPool: BitmapPool?,
    private val logger: InternalLogger,
    private val md5HashGenerator: MD5HashGenerator,
    private val recordedDataQueueHandler: DataQueueHandler,
    private val applicationId: String
) {
    private var isResourcesCacheRegisteredForCallbacks: Boolean = false
    private var isBitmapPoolRegisteredForCallbacks: Boolean = false

    // resource IDs previously sent in this session -
    // optimization to avoid sending the same resource multiple times
    // atm this set is unbounded but expected to use relatively little space (~80kb per 1k items)
    private val resourceIdsSeen: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

    // region internal

    @MainThread
    internal fun handleBitmap(
        resources: Resources,
        applicationContext: Context,
        displayMetrics: DisplayMetrics,
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ) {
        registerCallbacks(applicationContext)

        tryToGetResourceFromCache(
            drawable = drawable,
            imageWireframe = imageWireframe,
            resourcesSerializerCallback = resourcesSerializerCallback
        )
            ?: tryToGetBitmapFromBitmapDrawable(
                resources = resources,
                drawable = drawable,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback
            )
            ?: tryToDrawNewBitmap(
                resources = resources,
                drawable = drawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback,

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
        resources: Resources,
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        bitmap: Bitmap,
        shouldCacheBitmap: Boolean,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback,

        // this parameter is used to avoid infinite recursion
        // basically we only allow one attempt to recreate the bitmap
        didCallOriginateFromFailover: Boolean
    ) {
        val byteArray = webPImageCompression.compressBitmap(bitmap)

        // failed to get byteArray potentially because the bitmap was recycled before imageCompression
        // Try once to recreate bitmap from the drawable
        if (byteArray.isEmpty() && bitmap.isRecycled && !didCallOriginateFromFailover) {
            tryToDrawNewBitmap(
                resources = resources,
                drawable = drawable,
                drawableWidth = bitmap.width,
                drawableHeight = bitmap.height,
                displayMetrics = displayMetrics,
                imageWireframe = imageWireframe,
                resourcesSerializerCallback = resourcesSerializerCallback,
                didCallOriginateFromFailover = true
            )

            return
        }

        if (byteArray.isEmpty()) {
            // failed to get image data
            resourcesSerializerCallback.onReady()
            return
        }

        val resourceId = md5HashGenerator.generate(byteArray)

        if (shouldCacheBitmap) {
            bitmapPool?.put(bitmap)
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

        val resourceIdByteArray = resourceId.toByteArray(Charsets.UTF_8)
        resourcesLRUCache.put(drawable, resourceIdByteArray)

        finalizeRecordedDataItem(resourceIdByteArray, imageWireframe)
        resourcesSerializerCallback.onReady()
    }

    @MainThread
    private fun registerResourceLruCacheForCallbacks(applicationContext: Context) {
        if (isResourcesCacheRegisteredForCallbacks) return

        if (resourcesLRUCache is ComponentCallbacks2) {
            applicationContext.registerComponentCallbacks(resourcesLRUCache)
            isResourcesCacheRegisteredForCallbacks = true
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

    private fun tryToDrawNewBitmap(
        resources: Resources,
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback,
        didCallOriginateFromFailover: Boolean
    ) {
        drawableUtils.createBitmapOfApproxSizeFromDrawable(
            resources = resources,
            drawable = drawable,
            drawableWidth = drawableWidth,
            drawableHeight = drawableHeight,
            displayMetrics = displayMetrics,
            bitmapCreationCallback = object : BitmapCreationCallback {
                override fun onReady(bitmap: Bitmap) {
                    Runnable {
                        @Suppress("ThreadSafety") // this runs inside an executor
                        serializeBitmap(
                            resources = resources,
                            drawable = drawable,
                            displayMetrics = displayMetrics,
                            bitmap = bitmap,
                            shouldCacheBitmap = true,
                            imageWireframe = imageWireframe,
                            resourcesSerializerCallback = resourcesSerializerCallback,
                            didCallOriginateFromFailover = didCallOriginateFromFailover
                        )
                    }.let {
                        threadPoolExecutor.executeSafe("tryToDrawNewBitmap", logger, it)
                    }
                }

                override fun onFailure() {
                    resourcesSerializerCallback.onReady()
                }
            }
        )
    }

    @MainThread
    private fun tryToGetBitmapFromBitmapDrawable(
        resources: Resources,
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
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
                        resources = resources,
                        drawable = drawable,
                        displayMetrics = displayMetrics,
                        bitmap = scaledBitmap,
                        shouldCacheBitmap = shouldCacheBitmap,
                        imageWireframe = imageWireframe,
                        resourcesSerializerCallback = resourcesSerializerCallback,
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

    private fun tryToGetResourceFromCache(
        drawable: Drawable,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        resourcesSerializerCallback: ResourcesSerializerCallback
    ): String? {
        val resourceIdByteArray = resourcesLRUCache.get(drawable) ?: return null

        finalizeRecordedDataItem(resourceIdByteArray, imageWireframe)

        resourcesSerializerCallback.onReady()

        return String(resourceIdByteArray, Charsets.UTF_8)
    }

    private fun finalizeRecordedDataItem(
        resourceIdByteArray: ByteArray,
        wireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        val resourceId = String(resourceIdByteArray, Charsets.UTF_8)

        wireframe.resourceId = resourceId
        wireframe.isEmpty = false
    }

    private fun shouldUseDrawableBitmap(drawable: BitmapDrawable): Boolean {
        return drawable.bitmap != null &&
            !drawable.bitmap.isRecycled &&
            drawable.bitmap.width > 0 &&
            drawable.bitmap.height > 0
    }

    @MainThread
    private fun registerCallbacks(applicationContext: Context) {
        registerResourceLruCacheForCallbacks(applicationContext)
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
        private var resourcesLRUCache: Cache<Drawable, ByteArray>,
        private var drawableUtils: DrawableUtils = DrawableUtils(
            bitmapPool = bitmapPool,
            threadPoolExecutor = threadPoolExecutor,
            logger = logger
        ),
        private var webPImageCompression: ImageCompression = WebPImageCompression(),
        private var md5HashGenerator: MD5HashGenerator = MD5HashGenerator(logger)
    ) {
        internal fun build() =
            ResourcesSerializer(
                logger = logger,
                threadPoolExecutor = threadPoolExecutor,
                bitmapPool = bitmapPool,
                resourcesLRUCache = resourcesLRUCache,
                drawableUtils = drawableUtils,
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
