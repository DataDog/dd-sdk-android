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
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import android.util.DisplayMetrics
import android.view.View
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
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
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) {

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
        val pixelCount = bitmap.width.toLong() * bitmap.height
        val argbByteCount = pixelCount * ARGB_8888_PIXEL_SIZE_BYTES
        // Every bitmap is processed as ARGB_8888. An ALPHA_8 one is copied in its own format and converted on the
        // worker, which holds both, so until then the capture holds only its copy.
        val copyByteCount = if (isAlpha8) pixelCount else argbByteCount
        val processedByteCount = if (isAlpha8) copyByteCount + argbByteCount else copyByteCount
        // A capture with nothing else pending is admitted whatever its size, but one whose copy and conversion alone
        // would exceed the budget is not attempted, so that it cannot run the application out of memory.
        if (processedByteCount > pendingImageRequests.maxPendingBytes) {
            resourceResolverCallback.onFailure()
            return
        }
        val request = pendingImageRequests.start(
            key = resourceKey,
            byteCount = copyByteCount,
            callback = resourceResolverCallback
        ) ?: return
        // The application can change or recycle its bitmap once this returns, compressing a hardware bitmap fails,
        // and a queued task must not keep the application's bitmap alive, so the worker always gets a copy.
        var copy: Bitmap? = null
        try {
            copy = copyBitmap(bitmap, if (isAlpha8) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888)
        } finally {
            // A copy that fails or throws must not leave the captures waiting forever; a throwable still propagates.
            if (copy == null) request.onFailure()
        }
        val capturedBitmap = copy ?: return

        executeImageTask(request) {
            // The copy is recycled before the request completes, so its pixels stay reserved until they are freed.
            var resolvedResourceId: String? = null
            try {
                @Suppress("ThreadSafety") // This task runs on the resource executor.
                getResourceIdFromBitmap(
                    capturedBitmap,
                    object : ResourceResolverCallback {
                        override fun onSuccess(resourceId: String) {
                            resolvedResourceId = resourceId
                        }

                        override fun onFailure() {}
                    }
                )
            } finally {
                capturedBitmap.recycle()
            }
            val resourceId = resolvedResourceId
            if (resourceId == null) {
                request.onFailure()
            } else {
                bitmapCachesManager.putInResourceCache(resourceKey, resourceId)
                request.onSuccess(resourceId)
            }
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
        val drawableKey = if (customResourceIdCacheKey != null) {
            "$customResourceIdCacheKey-${System.identityHashCode(originalDrawable)}"
        } else {
            resourceKey
        }
        // Queued work holds a drawable attached to a view weakly, so work for a drawable the application has since
        // replaced is skipped, unless the copier itself holds the drawable or its view, and it reserves nothing. A
        // detached drawable, such as one a custom mapper creates for the capture, or the child of one, has nothing else
        // holding it, so it is held strongly and reserves the bitmaps it holds.
        val isAttached = isAttachedToView(originalDrawable)
        // Captures merge only when they make the same image the same way: from the same drawable, held the same way, at
        // the same size and by the same copier.
        val holdKey = if (isAttached) "weak" else "strong"
        val request = pendingImageRequests.start(
            key = "$drawableKey-${copierKey(drawableCopier)}-$holdKey-${drawableWidth}x$drawableHeight",
            byteCount = if (isAttached) 0L else heldBitmapBytes(originalDrawable, depth = 0),
            callback = resourceResolverCallback
        ) ?: return
        val drawableReference = if (isAttached) {
            drawableReferenceFactory(originalDrawable)
        } else {
            null
        }
        val detachedDrawable = if (drawableReference == null) originalDrawable else null

        // do in the background
        executeImageTask(request) {
            val capturedDrawable = drawableReference?.get() ?: detachedDrawable
            if (capturedDrawable == null) {
                request.onFailure()
                return@executeImageTask
            }

            // Copy the drawable on work thread in order to avoid the new MaterialShapeDrawable
            // instance to reuse the singleton of `ShapeAppearancePathProvider` from main thread,
            // causing UI thread drawing crash.
            val copiedDrawable = copyDrawable(drawableCopier, capturedDrawable, resources)
            if (copiedDrawable == null) {
                request.onFailure()
                return@executeImageTask
            }

            val bitmapFromDrawable =
                if (copiedDrawable is BitmapDrawable && shouldUseDrawableBitmap(copiedDrawable)) {
                    copiedDrawable.bitmap // cannot be null - we already checked in shouldUseDrawableBitmap
                } else {
                    null
                }

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
    }

    // endregion

    // region private

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
        val handledBitmap = if (bitmapFromDrawable != null) {
            tryToGetBitmapFromBitmapDrawable(
                drawable = drawable,
                bitmapFromDrawable = bitmapFromDrawable,
                customResourceIdCacheKey = customResourceIdCacheKey,
                resolveResourceCallback = resolveResourceCallback
            )
        } else {
            null
        }

        if (handledBitmap == null) {
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
            customResourceIdCacheKey = customResourceIdCacheKey,
            drawable = drawable
        )

        resolveResourceCallback.onResolved(resourceId, compressedBitmapBytes)
    }

    private fun cacheIfNecessary(
        shouldCacheBitmap: Boolean,
        bitmap: Bitmap,
        resourceId: String,
        customResourceIdCacheKey: String?,
        drawable: Drawable?
    ) {
        if (shouldCacheBitmap) {
            bitmapCachesManager.putInBitmapPool(bitmap)
        }

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
            customResourceIdCacheKey = customResourceIdCacheKey,
            resolveResourceCallback = resolveResourceCallback
        )

        return scaledBitmap
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

    private fun executeImageTask(request: ResourceResolverCallback, action: () -> Unit) {
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
        // What executeSafe does for this plain executor, whose tasks it does not name, and the rejected capture fails.
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here; rejection is handled below.
            threadPoolExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            logger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { RESOURCE_TASK_REJECTED },
                e
            )
            request.onFailure()
        }
    }

    // A copier can be configured, so only the same instance counts as the same copier, except the default one, which has
    // no state of its own.
    private fun copierKey(drawableCopier: DrawableCopier): String =
        if (drawableCopier is DefaultDrawableCopier) "default" else System.identityHashCode(drawableCopier).toString()

    // A copier can be application code, whose exceptions are unknown, so one that throws fails the capture.
    @Suppress("TooGenericExceptionCaught")
    private fun copyDrawable(drawableCopier: DrawableCopier, drawable: Drawable, resources: Resources): Drawable? {
        return try {
            drawableCopier.copy(drawable, resources)
        } catch (e: Exception) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { DRAWABLE_COPY_FAILED },
                e,
                onlyOnce = true
            )
            null
        }
    }

    private fun copyBitmap(bitmap: Bitmap, config: Bitmap.Config): Bitmap? {
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

    // Changing the pixels of a bitmap changes its generation, but changing its alpha flags or color space, which affect
    // how it is drawn and encoded, does not, so the key holds them too to identify this exact image.
    private fun generateBitmapKey(bitmap: Bitmap): String =
        "$BITMAP_KEY_PREFIX${System.identityHashCode(bitmap)}-${bitmap.generationId}-" +
            "${bitmap.width}x${bitmap.height}-${bitmap.config}-${bitmap.hasAlpha()}-${bitmap.isPremultiplied}-" +
            "${colorSpaceKey(bitmap)}"

    // Every custom color space has the same id, so the color space is compared by value. Reading it throws when the
    // application recycles its bitmap from another thread meanwhile, which the copy then reports.
    @Suppress("SwallowedException")
    private fun colorSpaceKey(bitmap: Bitmap): Int? {
        if (!buildSdkVersionProvider.isAtLeastO) return null
        return try {
            bitmap.colorSpace?.hashCode()
        } catch (e: IllegalStateException) {
            null
        }
    }

    // The pixels of the bitmaps a detached drawable keeps alive while its capture waits: its own when it is a bitmap
    // drawable, and those of the wrappers and layers in it and of the state a container has selected. The other states
    // of a container cannot be read without creating them, which can race with a copy of the same drawable on the
    // worker, and those a custom drawable holds cannot be read at all, so they are not counted.
    private fun heldBitmapBytes(drawable: Drawable?, depth: Int): Long {
        if (drawable == null || depth >= MAX_WRAPPER_DEPTH) return 0L
        return when (drawable) {
            is BitmapDrawable -> {
                val bitmap = drawable.bitmap
                if (bitmap != null && !bitmap.isRecycled) bitmap.allocationByteCount.toLong() else 0L
            }
            is DrawableContainer -> heldBitmapBytes(drawable.current, depth + 1)
            is DrawableWrapper -> heldBitmapBytes(drawable.drawable, depth + 1)
            is LayerDrawable -> {
                var bytes = 0L
                repeat(drawable.numberOfLayers) { index ->
                    @Suppress("UnsafeThirdPartyFunctionCall") // The index is within the layers.
                    bytes += heldBitmapBytes(drawable.getDrawable(index), depth + 1)
                }
                bytes
            }
            else -> 0L
        }
    }

    private fun shouldUseDrawableBitmap(drawable: BitmapDrawable): Boolean {
        return drawable.bitmap != null &&
            !drawable.bitmap.isRecycled &&
            drawable.bitmap.width > 0 &&
            drawable.bitmap.height > 0
    }

    // A wrapper such as a LayerDrawable or an InsetDrawable is the callback of the drawables it holds, and the drawable
    // captured can be one of those, so a drawable is attached to a view only when its callbacks, followed through the
    // drawables holding it, end at a view.
    private fun isAttachedToView(drawable: Drawable): Boolean {
        var callback = drawable.callback
        var depth = 0
        while (callback is Drawable && depth < MAX_WRAPPER_DEPTH) {
            callback = callback.callback
            depth++
        }
        return callback is View
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
        private const val RESOURCE_TASK_REJECTED = "Unable to schedule $RESOURCE_RESOLVER_ALIAS task on the executor"
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
        private const val BITMAP_COPY_FAILED = "Unable to copy a bitmap for Session Replay"
        private const val DRAWABLE_COPY_FAILED = "Unable to copy a drawable for Session Replay"

        // Deeper wrapper chains, or a cycle of callbacks, are treated as detached, which holds the drawable strongly.
        private const val MAX_WRAPPER_DEPTH = 16

        // Drawable keys hold only digits, dashes and drawable states, so they never start with this prefix.
        private const val BITMAP_KEY_PREFIX = "bitmap-"

        // With an unbounded queue only the core thread ever runs, so captures are processed one at a time, which the
        // reservations in PendingImageRequests rely on.
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
