/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class ResourceResolver(
    private val bitmapCachesManager: BitmapCachesManager,
    internal val threadPoolExecutor: ExecutorService = THREADPOOL_EXECUTOR,
    private val drawableUtils: DrawableUtils,
    private val webPImageCompression: ImageCompression,
    private val logger: InternalLogger,
    private val md5HashGenerator: MD5HashGenerator,
    private val recordedDataQueueHandler: DataQueueHandler,
    private val applicationId: String,
    private val resourceItemCreationHandler: ResourceItemCreationHandler = ResourceItemCreationHandler(
        recordedDataQueueHandler = recordedDataQueueHandler,
        applicationId = applicationId
    )
) {

    // region internal

    @MainThread
    internal fun resolveResourceId(
        bitmap: Bitmap,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        threadPoolExecutor.executeSafe("resolveResourceId", logger) {
            val compressedBitmapBytes = webPImageCompression.compressBitmap(bitmap)

            // failed to compress bitmap
            if (compressedBitmapBytes.isEmpty()) {
                resourceResolverCallback.onFailure()
            } else {
                resolveBitmapHash(
                    compressedBitmapBytes = compressedBitmapBytes,
                    resolveResourceCallback = object : ResolveResourceCallback {
                        override fun onResolved(resourceId: String, resourceData: ByteArray) {
                            resourceItemCreationHandler.queueItem(resourceId, resourceData)
                            resourceResolverCallback.onSuccess(resourceId)
                        }

                        override fun onFailed() {
                            resourceResolverCallback.onFailure()
                        }
                    }
                )
            }
        }
    }

    // endregion
    @MainThread
    internal fun resolveResourceId(
        resources: Resources,
        applicationContext: Context,
        displayMetrics: DisplayMetrics,
        originalDrawable: Drawable,
        drawableCopier: DrawableCopier,
        drawableWidth: Int,
        drawableHeight: Int,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        bitmapCachesManager.registerCallbacks(applicationContext)

        val resourceId = tryToGetResourceFromCache(drawable = originalDrawable)

        if (resourceId != null) {
            // if we got here it means we saw the bitmap before,
            // so we don't need to send the resource again
            resourceResolverCallback.onSuccess(resourceId)
            return
        }

        val copiedDrawable = drawableCopier.copy(originalDrawable, resources) ?: return

        val bitmapFromDrawable =
            if (copiedDrawable is BitmapDrawable && shouldUseDrawableBitmap(copiedDrawable)) {
                copiedDrawable.bitmap // cannot be null - we already checked in shouldUseDrawableBitmap
            } else {
                null
            }

        // do in the background
        threadPoolExecutor.executeSafe("resolveResourceId", logger) {
            createBitmap(
                resources = resources,
                drawable = originalDrawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                bitmapFromDrawable = bitmapFromDrawable,
                resolveResourceCallback = object : ResolveResourceCallback {
                    override fun onResolved(resourceId: String, resourceData: ByteArray) {
                        resourceItemCreationHandler.queueItem(resourceId, resourceData)
                        resourceResolverCallback.onSuccess(resourceId)
                    }

                    override fun onFailed() {
                        resourceResolverCallback.onFailure()
                    }
                }
            )
        }
    }

    // endregion

    // region private

    @WorkerThread
    private fun createBitmap(
        resources: Resources,
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        bitmapFromDrawable: Bitmap?,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        val handledBitmap = if (bitmapFromDrawable != null) {
            tryToGetBitmapFromBitmapDrawable(
                drawable = drawable as BitmapDrawable,
                bitmapFromDrawable = bitmapFromDrawable,
                resolveResourceCallback = resolveResourceCallback
            )
        } else {
            null
        }

        if (handledBitmap == null) {
            tryToDrawNewBitmap(
                resources = resources,
                drawable = drawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                resolveResourceCallback = resolveResourceCallback
            )
        }
    }

    @WorkerThread
    private fun resolveBitmapHash(
        compressedBitmapBytes: ByteArray,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        // failed to get image data
        if (compressedBitmapBytes.isEmpty()) {
            // we are already logging the failure in webpImageCompression
            resolveResourceCallback.onFailed()
            return
        }

        val resourceId = md5HashGenerator.generate(compressedBitmapBytes)

        // failed to resolve bitmap identifier
        if (resourceId == null) {
            // logging md5 generation failures inside md5HashGenerator
            resolveResourceCallback.onFailed()
            return
        }
        resolveResourceCallback.onResolved(resourceId, compressedBitmapBytes)
    }

    @Suppress("ReturnCount")
    @WorkerThread
    private fun resolveResourceHash(
        drawable: Drawable,
        bitmap: Bitmap,
        compressedBitmapBytes: ByteArray,
        shouldCacheBitmap: Boolean,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        // failed to get image data
        if (compressedBitmapBytes.isEmpty()) {
            // we are already logging the failure in webpImageCompression
            resolveResourceCallback.onFailed()
            return
        }

        val resourceId = md5HashGenerator.generate(compressedBitmapBytes)

        // failed to resolve bitmap identifier
        if (resourceId == null) {
            // logging md5 generation failures inside md5HashGenerator
            resolveResourceCallback.onFailed()
            return
        }

        cacheIfNecessary(
            shouldCacheBitmap = shouldCacheBitmap,
            bitmap = bitmap,
            resourceId = resourceId,
            drawable = drawable
        )

        resolveResourceCallback.onResolved(resourceId, compressedBitmapBytes)
    }

    private fun cacheIfNecessary(
        shouldCacheBitmap: Boolean,
        bitmap: Bitmap,
        resourceId: String,
        drawable: Drawable
    ) {
        if (shouldCacheBitmap) {
            bitmapCachesManager.putInBitmapPool(bitmap)
        }

        bitmapCachesManager.putInResourceCache(drawable, resourceId)
    }

    @WorkerThread
    private fun tryToDrawNewBitmap(
        resources: Resources,
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        drawableUtils.createBitmapOfApproxSizeFromDrawable(
            resources = resources,
            drawable = drawable,
            drawableWidth = drawableWidth,
            drawableHeight = drawableHeight,
            displayMetrics = displayMetrics,
            bitmapCreationCallback = object : BitmapCreationCallback {
                @WorkerThread
                override fun onReady(bitmap: Bitmap) {
                    val compressedBitmapBytes = webPImageCompression.compressBitmap(bitmap)

                    // failed to compress bitmap
                    if (compressedBitmapBytes.isEmpty()) {
                        resolveResourceCallback.onFailed()
                        return
                    }

                    resolveResourceHash(
                        drawable = drawable,
                        bitmap = bitmap,
                        compressedBitmapBytes = compressedBitmapBytes,
                        shouldCacheBitmap = true,
                        resolveResourceCallback = resolveResourceCallback
                    )
                }

                @WorkerThread
                override fun onFailure() {
                    resolveResourceCallback.onFailed()
                }
            }
        )
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun tryToGetBitmapFromBitmapDrawable(
        drawable: BitmapDrawable,
        bitmapFromDrawable: Bitmap,
        resolveResourceCallback: ResolveResourceCallback
    ): Bitmap? {
        val scaledBitmap = drawableUtils.createScaledBitmap(bitmapFromDrawable)
            ?: return null

        val compressedBitmapBytes = webPImageCompression.compressBitmap(scaledBitmap)

        // failed to get byteArray potentially because the bitmap was recycled before imageCompression
        if (compressedBitmapBytes.isEmpty()) {
            return null
        }

        /**
         * Check whether the scaled bitmap is the same as the original.
         * Since Bitmap.createScaledBitmap will return the original bitmap if the
         * requested dimensions match the dimensions of the original
         * Add a specific check for isRecycled, because getting width/height from a recycled bitmap
         * is undefined behavior
         */
        val shouldCacheBitmap = !bitmapFromDrawable.isRecycled && (
            scaledBitmap.width < bitmapFromDrawable.width ||
                scaledBitmap.height < bitmapFromDrawable.height
            )

        resolveResourceHash(
            drawable = drawable,
            bitmap = scaledBitmap,
            compressedBitmapBytes = compressedBitmapBytes,
            shouldCacheBitmap = shouldCacheBitmap,
            resolveResourceCallback = resolveResourceCallback
        )

        return scaledBitmap
    }

    private fun tryToGetResourceFromCache(
        drawable: Drawable
    ): String? = bitmapCachesManager.getFromResourceCache(drawable)

    private fun shouldUseDrawableBitmap(drawable: BitmapDrawable): Boolean {
        return drawable.bitmap != null &&
            !drawable.bitmap.isRecycled &&
            drawable.bitmap.width > 0 &&
            drawable.bitmap.height > 0
    }

    // endregion

    internal interface BitmapCreationCallback {

        @WorkerThread
        fun onReady(bitmap: Bitmap)

        @WorkerThread
        fun onFailure()
    }

    // endregion

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
