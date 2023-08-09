/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.recorder.base64.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("UndocumentedPublicClass")
class Base64Serializer private constructor(
    private val threadPoolExecutor: ExecutorService,
    private val drawableUtils: DrawableUtils,
    private val base64Utils: Base64Utils,
    private val webPImageCompression: ImageCompression,
    private val base64LruCache: Cache<Drawable, String>,
    private val bitmapPool: Cache<String, Bitmap>,
    private val logger: InternalLogger
) {
    private var asyncImageProcessingCallback: AsyncImageProcessingCallback? = null

    // region internal

    @MainThread
    internal fun handleBitmap(
        applicationContext: Context,
        displayMetrics: DisplayMetrics,
        drawable: Drawable,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        registerCacheForCallbacks(applicationContext)

        asyncImageProcessingCallback?.startProcessingImage()

        val cachedBase64 = base64LruCache.get(drawable)
        if (cachedBase64 != null) {
            finalizeRecordedDataItem(cachedBase64, imageWireframe, asyncImageProcessingCallback)
            return
        }

        val bitmap = drawableUtils.createBitmapOfApproxSizeFromDrawable(
            applicationContext,
            drawable,
            displayMetrics
        )

        if (bitmap == null) {
            asyncImageProcessingCallback?.finishProcessingImage()
            return
        }

        Runnable {
            @Suppress("ThreadSafety") // this runs inside an executor
            serialiseBitmap(drawable, bitmap, imageWireframe, asyncImageProcessingCallback)
        }.let { executeRunnable(it) }
    }

    internal fun registerAsyncLoadingCallback(
        asyncImageProcessingCallback: AsyncImageProcessingCallback
    ) {
        this.asyncImageProcessingCallback = asyncImageProcessingCallback
    }

    // endregion

    // region testing

    @VisibleForTesting
    internal fun getThreadPoolExecutor(): ExecutorService = threadPoolExecutor

    // endregion

    // region private

    @WorkerThread
    private fun serialiseBitmap(
        drawable: Drawable,
        bitmap: Bitmap,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        asyncImageProcessingCallback: AsyncImageProcessingCallback?
    ) {
        val base64String = convertBmpToBase64(drawable, bitmap)
        finalizeRecordedDataItem(base64String, imageWireframe, asyncImageProcessingCallback)
    }

    @MainThread
    private fun registerCacheForCallbacks(applicationContext: Context) {
        if (isCacheRegisteredForCallbacks) return

        if (base64LruCache is ComponentCallbacks2) {
            applicationContext.registerComponentCallbacks(base64LruCache)
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

    @WorkerThread
    private fun convertBmpToBase64(drawable: Drawable, bitmap: Bitmap): String {
        val base64Result: String

        val byteArray = webPImageCompression.compressBitmap(bitmap)

        base64Result = base64Utils.serializeToBase64String(byteArray)

        if (base64Result.isNotEmpty()) {
            // if we got a base64 string then cache it
            base64LruCache.put(drawable, base64Result)
        }

        bitmapPool.put(bitmap)

        return base64Result
    }

    private fun finalizeRecordedDataItem(
        base64String: String,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        asyncImageProcessingCallback: AsyncImageProcessingCallback?
    ) {
        if (base64String.isNotEmpty()) {
            wireframe.base64 = base64String
            wireframe.isEmpty = false
        }

        asyncImageProcessingCallback?.finishProcessingImage()
    }

    private fun executeRunnable(runnable: Runnable) {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            threadPoolExecutor.submit(runnable)
        } catch (e: RejectedExecutionException) {
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
        } catch (e: NullPointerException) {
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            // should never happen since task is not null
        }
    }

    // endregion

    // region builder
    internal class Builder {
        internal fun build(
            threadPoolExecutor: ExecutorService = THREADPOOL_EXECUTOR,
            drawableUtils: DrawableUtils = DrawableUtils(),
            base64Utils: Base64Utils = Base64Utils(),
            webPImageCompression: ImageCompression = WebPImageCompression(),
            base64LruCache: Cache<Drawable, String> = Base64LRUCache,
            bitmapPool: Cache<String, Bitmap> = BitmapPool,
            // Temporarily use UNBOUND until we handle the loggers
            logger: InternalLogger = InternalLogger.UNBOUND
        ) =
            Base64Serializer(
                threadPoolExecutor = threadPoolExecutor,
                drawableUtils = drawableUtils,
                base64Utils = base64Utils,
                webPImageCompression = webPImageCompression,
                base64LruCache = base64LruCache,
                bitmapPool = bitmapPool,
                logger = logger
            )

        private companion object {
            private const val THREAD_POOL_MAX_KEEP_ALIVE_MS = 5000L
            private const val CORE_DEFAULT_POOL_SIZE = 1
            private const val MAX_THREAD_COUNT = 10

            // all parameters are non-negative and queue is not null
            @Suppress("UnsafeThirdPartyFunctionCall")
            private val THREADPOOL_EXECUTOR = ThreadPoolExecutor(
                CORE_DEFAULT_POOL_SIZE,
                MAX_THREAD_COUNT,
                THREAD_POOL_MAX_KEEP_ALIVE_MS,
                TimeUnit.MILLISECONDS,
                LinkedBlockingDeque()
            )
        }
    }

    // endregion

    internal companion object {
        // The cache is a singleton, so we want to share this flag among
        // all instances so that it's registered only once
        @VisibleForTesting
        internal var isCacheRegisteredForCallbacks: Boolean = false
    }
}
