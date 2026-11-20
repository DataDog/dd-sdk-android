/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class ResourceResolver(
    private val applicationContext: Context,
    private val bitmapCachesManager: BitmapCachesManager,
    private val pathUtils: PathUtils,
    internal val threadPoolExecutor: ExecutorService = THREADPOOL_EXECUTOR,
    private val drawableUtils: DrawableUtils,
    private val webPImageCompression: ImageCompression,
    private val logger: InternalLogger,
    private val md5HashGenerator: MD5HashGenerator,
    recordedDataQueueHandler: DataQueueHandler,
    private val alpha8BitmapConverter: BitmapConverter = Alpha8BitmapConverter(logger),
    private val alpha8ResourceCache: Alpha8ResourceCache = DefaultAlpha8ResourceCache(
        signatureGenerator = DefaultBitmapSignatureGenerator()
    ),
    private val resourceItemCreationHandler: ResourceItemCreationHandler = ResourceItemCreationHandler(
        recordedDataQueueHandler = recordedDataQueueHandler
    ),
    private val pendingImageRequests: PendingImageRequests = PendingImageRequests(logger),
    private val drawableReferenceFactory: (Drawable) -> WeakReference<Drawable> = { WeakReference(it) },
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(logger)
) {
    private val reservationEstimator =
        ImageReservationEstimator(buildSdkVersionProvider, pendingImageRequests.maxPendingBytes)

    internal fun registerCallbacks() {
        applicationContext.registerComponentCallbacks(alpha8ResourceCache)
        bitmapCachesManager.registerCallbacks(applicationContext)
    }

    internal fun unregisterCallbacks() {
        applicationContext.unregisterComponentCallbacks(alpha8ResourceCache)
        bitmapCachesManager.unregisterCallbacks(applicationContext)
    }

    // region internal

    @MainThread
    @Suppress("ReturnCount")
    internal fun resolveResourceIdFromBitmap(
        bitmap: Bitmap,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        if (bitmap.isRecycled) {
            resourceResolverCallback.onFailure()
            return
        }
        // An unchanged bitmap is recognized before any copy, so it costs no pixels or compression.
        val resourceKey = generateBitmapKey(bitmap)
        val resourceId = bitmapCachesManager.getFromResourceCache(resourceKey)
        if (resourceId != null) {
            resourceResolverCallback.onSuccess(resourceId)
            return
        }

        val isAlpha8 = bitmap.config == Bitmap.Config.ALPHA_8
        val isHardware = reservationEstimator.isHardwareBitmap(bitmap)
        // The application can change or recycle its bitmap once this returns, so the worker gets a bitmap of its own:
        // a copy, an ALPHA_8 copy it converts, a hardware bitmap's full readback it scales, or from API 31 a wrapper of
        // a hardware bitmap's immutable buffer, which costs no readback here because the worker reads it back.
        val canWrap = isHardware && buildSdkVersionProvider.isAtLeastS
        // Every bitmap is processed as ARGB_8888, whether copied here or converted from ALPHA_8 on the worker.
        val byteCount = reservationEstimator.argbBytes(bitmap)
        if (reservationEstimator.isTooLargeToConvert(bitmap)) {
            resourceResolverCallback.onFailure()
            return
        }
        val request = startRequest(
            resourceKey = resourceKey,
            requestKey = resourceKey,
            byteCount = reservationEstimator.estimateBitmapBytes(bitmap, byteCount, isAlpha8, isHardware, canWrap),
            callback = resourceResolverCallback
        ) ?: return
        // A buffer that cannot be wrapped, for example in a format the wrapper rejects, is read back here instead.
        val wrappedBitmap = if (canWrap) bitmapWrapper.wrapHardwareBuffer(bitmap) else null
        val isWrapped = wrappedBitmap != null
        val capturedBitmap = when {
            isAlpha8 -> copyPixels(bitmap, Bitmap.Config.ALPHA_8)
            wrappedBitmap != null -> wrappedBitmap
            isHardware -> copyPixels(bitmap, Bitmap.Config.ARGB_8888)
            else -> copyBitmap(bitmap, byteCount, isHardware = false)
        }
        if (capturedBitmap == null) {
            request.onFailure()
            return
        }
        val prepareOnWorker: (Bitmap) -> Bitmap? = when {
            isWrapped -> { captured -> copyBitmap(captured, byteCount, isHardware = true) }
            isHardware && reservationEstimator.isScaledDown(bitmap, byteCount) -> { captured ->
                drawableUtils.createScaledArgb8888Bitmap(captured)
            }
            else -> { captured -> captured }
        }

        val isScheduled = executeImageTask(request) {
            @Suppress("ThreadSafety") // This task runs on the resource executor.
            processCapturedBitmap(capturedBitmap, resourceKey, request, prepareOnWorker)
        }
        if (!isScheduled) {
            capturedBitmap.recycle()
            request.onFailure()
        }
    }

    @MainThread
    internal fun resolveResourceIdFromPath(
        path: Path,
        strokeColor: Int,
        strokeWidth: Int,
        desiredWidth: Int,
        desiredHeight: Int,
        customResourceIdCacheKey: String?,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        threadPoolExecutor.executeSafe(RESOURCE_RESOLVER_ALIAS, logger) {
            val key =
                customResourceIdCacheKey
                    ?: path.let {
                        pathUtils.generateKeyForPath(it)
                    }

            val resourceId = tryToGetResourceFromCache(
                drawable = null,
                customResourceIdCacheKey = key
            )

            if (resourceId != null) {
                // if we got here it means we saw the bitmap before,
                // so we don't need to send the resource again
                resourceResolverCallback.onSuccess(resourceId)
                return@executeSafe
            }

            val bitmap = pathUtils.convertPathToBitmap(
                checkPath = path,
                checkmarkColor = strokeColor,
                desiredWidth = desiredWidth,
                desiredHeight = desiredHeight,
                strokeWidth = strokeWidth
            )

            if (bitmap == null) {
                resourceResolverCallback.onFailure()
                return@executeSafe
            }

            compressAndCacheBitmap(
                drawable = null,
                bitmap = bitmap,
                customResourceIdCacheKey = customResourceIdCacheKey,
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

    @MainThread
    internal fun resolveResourceIdFromDrawable(
        resources: Resources,
        displayMetrics: DisplayMetrics,
        originalDrawable: Drawable,
        drawableCopier: DrawableCopier,
        drawableWidth: Int,
        drawableHeight: Int,
        customResourceIdCacheKey: String?,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        val resourceKey =
            customResourceIdCacheKey ?: bitmapCachesManager.generateResourceKeyFromDrawable(originalDrawable)
        val resourceId = bitmapCachesManager.getFromResourceCache(resourceKey)

        if (resourceId != null) {
            // if we got here it means we saw the bitmap before,
            // so we don't need to send the resource again
            resourceResolverCallback.onSuccess(resourceId)
            return
        }

        // Built-in keys already identify the drawable instance, but a custom key can be shared by several, which are
        // captured separately so that each keeps its own drawable.
        val requestKey = if (customResourceIdCacheKey != null) {
            "$customResourceIdCacheKey-${System.identityHashCode(originalDrawable)}"
        } else {
            resourceKey
        }
        val request = startRequest(
            resourceKey = resourceKey,
            requestKey = requestKey,
            byteCount = reservationEstimator.estimateDrawableBytes(originalDrawable, drawableWidth, drawableHeight),
            callback = resourceResolverCallback
        ) ?: return
        // Queued work holds the drawable weakly, so work for a drawable the application has since replaced is skipped,
        // unless the copier itself holds the drawable or its view.
        val drawableReference = drawableReferenceFactory(originalDrawable)

        // do in the background
        val isScheduled = executeImageTask(request) {
            val capturedDrawable = drawableReference.get()
            if (capturedDrawable == null) {
                request.onFailure()
                return@executeImageTask
            }

            // Copy the drawable on work thread in order to avoid the new MaterialShapeDrawable
            // instance to reuse the singleton of `ShapeAppearancePathProvider` from main thread,
            // causing UI thread drawing crash.
            val copiedDrawable = drawableCopier.copy(capturedDrawable, resources)
            if (copiedDrawable == null) {
                request.onFailure()
                return@executeImageTask
            }

            val bitmapFromDrawable = reservedDrawableBitmap(copiedDrawable, request)

            @Suppress("ThreadSafety") // This task runs on the resource executor.
            createBitmapFromDrawable(
                drawable = capturedDrawable,
                copiedDrawable = copiedDrawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                bitmapFromDrawable = bitmapFromDrawable,
                customResourceIdCacheKey = customResourceIdCacheKey,
                resolveResourceCallback = object : ResolveResourceCallback {
                    override fun onResolved(resourceId: String, resourceData: ByteArray) {
                        resourceItemCreationHandler.queueItem(resourceId, resourceData)
                        request.onSuccess(resourceId)
                    }

                    override fun onFailed() {
                        request.onFailure()
                    }
                }
            )
        }
        if (!isScheduled) request.onFailure()
    }

    // endregion

    // region private

    // Work for the same image can complete between the cache lookup and admission. It publishes its resource before it
    // completes, so a capture admitted after it uses that resource instead of repeating the work.
    @MainThread
    private fun startRequest(
        resourceKey: String,
        requestKey: String,
        byteCount: Long,
        callback: ResourceResolverCallback
    ): PendingImageRequests.Request? {
        val request = pendingImageRequests.start(key = requestKey, byteCount = byteCount, callback = callback)
            ?: return null
        val publishedResourceId = bitmapCachesManager.getFromResourceCache(resourceKey)
        return if (publishedResourceId == null) {
            request
        } else {
            request.onSuccess(publishedResourceId)
            null
        }
    }

    @WorkerThread
    private fun createBitmapFromDrawable(
        drawable: Drawable,
        copiedDrawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        bitmapFromDrawable: Bitmap?,
        customResourceIdCacheKey: String?,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        val isHandled = if (bitmapFromDrawable != null) {
            tryToGetBitmapFromBitmapDrawable(
                drawable = drawable,
                bitmapFromDrawable = bitmapFromDrawable,
                customResourceIdCacheKey = customResourceIdCacheKey,
                resolveResourceCallback = resolveResourceCallback
            )
        } else {
            false
        }
        if (isHandled) return

        if (cannotDrawIntoRaster(copiedDrawable)) {
            resolveResourceCallback.onFailed()
        } else {
            tryToDrawNewBitmap(
                originalDrawable = drawable,
                copiedDrawable = copiedDrawable,
                drawableWidth = drawableWidth,
                drawableHeight = drawableHeight,
                displayMetrics = displayMetrics,
                customResourceIdCacheKey = customResourceIdCacheKey,
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
        drawable: Drawable?,
        bitmap: Bitmap,
        compressedBitmapBytes: ByteArray,
        shouldCacheBitmap: Boolean,
        customResourceIdCacheKey: String?,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        // A bitmap the pool can reuse goes back to it whether or not the resource resolves, before the capture
        // completes and returns its budget.
        if (shouldCacheBitmap) {
            bitmapCachesManager.putInBitmapPool(bitmap)
        }

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

        cacheResourceId(
            resourceId = resourceId,
            customResourceIdCacheKey = customResourceIdCacheKey,
            drawable = drawable
        )

        resolveResourceCallback.onResolved(resourceId, compressedBitmapBytes)
    }

    private fun cacheResourceId(
        resourceId: String,
        customResourceIdCacheKey: String?,
        drawable: Drawable?
    ) {
        val key = customResourceIdCacheKey
            ?: generateKey(drawable)
            ?: return

        bitmapCachesManager.putInResourceCache(key, resourceId)
    }

    @WorkerThread
    private fun tryToDrawNewBitmap(
        originalDrawable: Drawable,
        copiedDrawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        customResourceIdCacheKey: String?,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        drawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = copiedDrawable,
            drawableWidth = drawableWidth,
            drawableHeight = drawableHeight,
            displayMetrics = displayMetrics,
            bitmapCreationCallback = object : BitmapCreationCallback {
                @WorkerThread
                override fun onReady(bitmap: Bitmap) {
                    compressAndCacheBitmap(
                        drawable = originalDrawable,
                        bitmap = bitmap,
                        customResourceIdCacheKey = customResourceIdCacheKey,
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
    private fun compressAndCacheBitmap(
        drawable: Drawable?,
        bitmap: Bitmap,
        customResourceIdCacheKey: String?,
        resolveResourceCallback: ResolveResourceCallback
    ) {
        resolveResourceHash(
            drawable = drawable,
            bitmap = bitmap,
            compressedBitmapBytes = webPImageCompression.compressBitmap(bitmap),
            shouldCacheBitmap = true,
            customResourceIdCacheKey = customResourceIdCacheKey,
            resolveResourceCallback = resolveResourceCallback
        )
    }

    @WorkerThread
    private fun getResourceIdFromBitmap(bitmap: Bitmap, resourceResolverCallback: ResourceResolverCallback) {
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            getResourceIdFromAlpha8Bitmap(bitmap, resourceResolverCallback)
        } else {
            getResourceIdFromRegularBitmap(bitmap, resourceResolverCallback)
        }
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun getResourceIdFromAlpha8Bitmap(
        bitmap: Bitmap,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        // Generate cache key once and reuse for both lookup and storage
        val cacheKey = alpha8ResourceCache.generateKey(bitmap)

        if (cacheKey != null) {
            val cachedResourceId = alpha8ResourceCache.get(cacheKey)
            if (cachedResourceId != null) {
                resourceResolverCallback.onSuccess(cachedResourceId)
                return
            }
        }

        val convertedBitmap = alpha8BitmapConverter.convertAlpha8BitmapToArgb8888(bitmap)
        if (convertedBitmap == null) {
            resourceResolverCallback.onFailure()
            return
        }

        val compressedBitmapBytes = webPImageCompression.compressBitmap(convertedBitmap)
        convertedBitmap.recycle()

        if (compressedBitmapBytes.isEmpty()) {
            resourceResolverCallback.onFailure()
            return
        }

        resolveBitmapHash(
            compressedBitmapBytes = compressedBitmapBytes,
            resolveResourceCallback = object : ResolveResourceCallback {
                override fun onResolved(resourceId: String, resourceData: ByteArray) {
                    if (cacheKey != null) {
                        alpha8ResourceCache.put(cacheKey, resourceId)
                    }
                    resourceItemCreationHandler.queueItem(resourceId, resourceData)
                    resourceResolverCallback.onSuccess(resourceId)
                }

                override fun onFailed() {
                    resourceResolverCallback.onFailure()
                }
            }
        )
    }

    @WorkerThread
    private fun getResourceIdFromRegularBitmap(
        bitmap: Bitmap,
        resourceResolverCallback: ResourceResolverCallback
    ) {
        val compressedBitmapBytes = webPImageCompression.compressBitmap(bitmap)

        if (compressedBitmapBytes.isEmpty()) {
            resourceResolverCallback.onFailure()
            return
        }

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

    @WorkerThread
    @Suppress("ReturnCount")
    private fun tryToGetBitmapFromBitmapDrawable(
        drawable: Drawable,
        bitmapFromDrawable: Bitmap,
        customResourceIdCacheKey: String?,
        resolveResourceCallback: ResolveResourceCallback
    ): Boolean {
        // A hardware bitmap is read back into a copy of its own, like a captured bitmap, instead of being scaled
        // directly, which would read it back in full and upload the result to the GPU again. Scaling keeps a bitmap's
        // own format, so an RGBA_F16 one is drawn straight into ARGB_8888 to stay within the drawing limit.
        val isHardware = reservationEstimator.isHardwareBitmap(bitmapFromDrawable)
        val isUnpremultiplied = reservationEstimator.isUnpremultiplied(bitmapFromDrawable)
        val isRgbaF16 = isRgbaF16Bitmap(bitmapFromDrawable)
        val isOwnedCopy = isHardware || isUnpremultiplied || isRgbaF16
        val scaledBitmap = when {
            isHardware -> copyBitmap(
                bitmapFromDrawable,
                reservationEstimator.argbBytes(bitmapFromDrawable),
                isHardware = true
            )
            // A Canvas cannot draw a bitmap with unpremultiplied alpha, so one is copied at full size instead.
            isUnpremultiplied -> copyPixels(bitmapFromDrawable, Bitmap.Config.ARGB_8888)
            isRgbaF16 -> drawableUtils.createScaledArgb8888Bitmap(bitmapFromDrawable)
            else -> drawableUtils.createScaledBitmap(bitmapFromDrawable)
        } ?: return false

        val compressedBitmapBytes = webPImageCompression.compressBitmap(scaledBitmap)

        // failed to get byteArray potentially because the bitmap was recycled before imageCompression
        if (compressedBitmapBytes.isEmpty()) {
            if (scaledBitmap !== bitmapFromDrawable) scaledBitmap.recycle()
            return false
        }

        /**
         * Check whether the scaled bitmap is the same as the original.
         * Since Bitmap.createScaledBitmap will return the original bitmap if the
         * requested dimensions match the dimensions of the original
         * Add a specific check for isRecycled, because getting width/height from a recycled bitmap
         * is undefined behavior. A copy read back or converted to ARGB_8888 is released instead.
         */
        val shouldCacheBitmap = !isOwnedCopy && !bitmapFromDrawable.isRecycled && (
            scaledBitmap.width < bitmapFromDrawable.width ||
                scaledBitmap.height < bitmapFromDrawable.height
            )

        // A copy made here and not kept by the pool is released before the capture completes and returns its budget.
        var resolvedResource: Pair<String, ByteArray>? = null
        try {
            resolveResourceHash(
                drawable = drawable,
                bitmap = scaledBitmap,
                compressedBitmapBytes = compressedBitmapBytes,
                shouldCacheBitmap = shouldCacheBitmap,
                customResourceIdCacheKey = customResourceIdCacheKey,
                resolveResourceCallback = object : ResolveResourceCallback {
                    override fun onResolved(resourceId: String, resourceData: ByteArray) {
                        resolvedResource = resourceId to resourceData
                    }

                    override fun onFailed() {}
                }
            )
        } finally {
            if (scaledBitmap !== bitmapFromDrawable && !shouldCacheBitmap) scaledBitmap.recycle()
        }
        val resource = resolvedResource
        if (resource == null) {
            resolveResourceCallback.onFailed()
        } else {
            resolveResourceCallback.onResolved(resource.first, resource.second)
        }
        return true
    }

    private fun tryToGetResourceFromCache(
        drawable: Drawable?,
        customResourceIdCacheKey: String?
    ): String? {
        val key = customResourceIdCacheKey
            ?: generateKey(drawable)
            ?: return null

        return bitmapCachesManager.getFromResourceCache(key)
    }

    private fun generateKey(drawable: Drawable?): String? {
        return if (drawable != null) {
            bitmapCachesManager.generateResourceKeyFromDrawable(drawable)
        } else {
            null
        }
    }

    // Returns false when the task was rejected, so the caller can release what it holds before failing the capture.
    private fun executeImageTask(request: ResourceResolverCallback, action: () -> Unit): Boolean {
        val task = Runnable {
            var isHandedOff = false
            try {
                action()
                isHandedOff = true
            } finally {
                // An escaping throwable must not leave the captures waiting forever; it still propagates.
                if (!isHandedOff) request.onFailure()
            }
        }
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here; rejection is handled below.
            threadPoolExecutor.execute(task)
            true
        } catch (e: RejectedExecutionException) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { RESOURCE_TASK_REJECTED },
                e
            )
            false
        }
    }

    // Every bitmap the SDK owns for the capture is released before the request completes and returns its budget.
    // The worker reads back a wrapped hardware bitmap and scales a large hardware readback before compressing it.
    @WorkerThread
    private fun processCapturedBitmap(
        capturedBitmap: Bitmap,
        resourceKey: String,
        request: ResourceResolverCallback,
        prepareOnWorker: (Bitmap) -> Bitmap?
    ) {
        var resolvedResourceId: String? = null
        var preparedBitmap: Bitmap? = null
        var isCapturedBitmapReleased = false
        try {
            preparedBitmap = prepareOnWorker(capturedBitmap)
            // A full readback or a wrapper is released as soon as the bitmap prepared from it exists.
            if (preparedBitmap !== capturedBitmap) {
                capturedBitmap.recycle()
                isCapturedBitmapReleased = true
            }
            preparedBitmap?.let { bitmap ->
                getResourceIdFromBitmap(
                    bitmap,
                    object : ResourceResolverCallback {
                        override fun onSuccess(resourceId: String) {
                            resolvedResourceId = resourceId
                        }

                        override fun onFailure() {}
                    }
                )
            }
        } finally {
            if (preparedBitmap !== capturedBitmap) preparedBitmap?.recycle()
            if (!isCapturedBitmapReleased) capturedBitmap.recycle()
        }
        val resourceId = resolvedResourceId
        if (resourceId == null) {
            request.onFailure()
        } else {
            bitmapCachesManager.putInResourceCache(resourceKey, resourceId)
            request.onSuccess(resourceId)
        }
    }

    // A large image is scaled down to the size drawables are rasterized to instead of being kept at full size, and a
    // long one so that its long edge fits the WebP encoder.
    private fun copyBitmap(bitmap: Bitmap, byteCount: Long, isHardware: Boolean): Bitmap? {
        return when {
            !reservationEstimator.isScaledDown(bitmap, byteCount) -> copyPixels(bitmap, Bitmap.Config.ARGB_8888)
            // A hardware bitmap cannot be drawn into another bitmap, so it is read back once and that copy is scaled.
            isHardware -> copyPixels(bitmap, Bitmap.Config.ARGB_8888)?.let { copy ->
                drawableUtils.createScaledArgb8888Bitmap(copy).also { copy.recycle() }
            }
            // A Canvas cannot draw a bitmap with unpremultiplied alpha, so one is copied at full size instead.
            reservationEstimator.isUnpremultiplied(bitmap) -> copyPixels(bitmap, Bitmap.Config.ARGB_8888)
            else -> drawableUtils.createScaledArgb8888Bitmap(bitmap)
        }
    }

    private fun copyPixels(bitmap: Bitmap, config: Bitmap.Config): Bitmap? {
        return try {
            bitmap.copy(config, false)
        } catch (e: IllegalArgumentException) {
            logCopyFailure(e)
            null
        } catch (e: IllegalStateException) {
            // The application can recycle its bitmap from another thread.
            logCopyFailure(e)
            null
        }
    }

    private fun logCopyFailure(error: RuntimeException) {
        logger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { BITMAP_COPY_FAILED },
            error,
            onlyOnce = true
        )
    }

    private fun isRgbaF16Bitmap(bitmap: Bitmap): Boolean =
        buildSdkVersionProvider.isAtLeastO && bitmap.config == Bitmap.Config.RGBA_F16

    // Changing the pixels of a bitmap changes its generation, so the key identifies this exact content.
    private fun generateBitmapKey(bitmap: Bitmap): String =
        "$BITMAP_KEY_PREFIX${System.identityHashCode(bitmap)}-${bitmap.generationId}-" +
            "${bitmap.width}x${bitmap.height}-${bitmap.config}"

    // The reservation is made for the bitmap the captured drawable had. Another bitmap, returned by a custom copier or
    // set on the drawable since, is captured directly when the reservation can be raised to what it needs, and
    // otherwise takes the raster fallback.
    private fun reservedDrawableBitmap(copiedDrawable: Drawable, request: PendingImageRequests.Request): Bitmap? {
        if (copiedDrawable !is BitmapDrawable || !shouldUseDrawableBitmap(copiedDrawable)) return null
        val bitmap = copiedDrawable.bitmap // cannot be null - we already checked in shouldUseDrawableBitmap
        val isReserved = !reservationEstimator.isTooLargeToCopyInFull(bitmap) &&
            request.reserve(reservationEstimator.drawableBitmapBytes(bitmap))
        return if (isReserved) bitmap else null
    }

    // A software Canvas cannot draw a recycled bitmap or a hardware one, nor, outside a tiling shader, one with
    // unpremultiplied alpha, so a bitmap drawable with such a bitmap would fail to draw into a raster too.
    private fun cannotDrawIntoRaster(drawable: Drawable): Boolean {
        val bitmapDrawable = drawable as? BitmapDrawable
        val bitmap = bitmapDrawable?.bitmap
        val isTiled = bitmapDrawable?.tileModeX != null || bitmapDrawable?.tileModeY != null
        return bitmap != null && (
            bitmap.isRecycled ||
                reservationEstimator.isHardwareBitmap(bitmap) ||
                (!isTiled && reservationEstimator.isUnpremultiplied(bitmap))
            )
    }

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
        private const val RESOURCE_RESOLVER_ALIAS = "resolveResourceId"
        private const val RESOURCE_TASK_REJECTED = "Unable to schedule Session Replay image processing"
        private const val BITMAP_COPY_FAILED = "Unable to copy a bitmap for Session Replay"

        // Drawable keys hold only digits, dashes and drawable states, so they never start with this prefix.
        private const val BITMAP_KEY_PREFIX = "bitmap-"

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
