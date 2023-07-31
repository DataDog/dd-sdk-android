/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
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
    private val drawableUtils: DrawableUtils = DrawableUtils(),
    private val base64Utils: Base64Utils = Base64Utils(),
    private val webPImageCompression: ImageCompression = WebPImageCompression()
) {

    private var asyncImageProcessingCallback: AsyncImageProcessingCallback? = null

    // region internal

    @MainThread
    internal fun handleBitmap(
        displayMetrics: DisplayMetrics,
        drawable: Drawable,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        asyncImageProcessingCallback?.startProcessingImage()

        val bitmap = drawableUtils.createBitmapFromDrawable(drawable, displayMetrics)

        if (bitmap == null) {
            asyncImageProcessingCallback?.finishProcessingImage()
            return
        }

        Runnable {
            @Suppress("ThreadSafety") // this runs inside an executor
            serialiseBitmap(bitmap, imageWireframe, asyncImageProcessingCallback)
        }.let { executeRunnable(it) }
    }

    internal fun registerAsyncLoadingCallback(
        asyncImageProcessingCallback: AsyncImageProcessingCallback
    ) {
        this.asyncImageProcessingCallback = asyncImageProcessingCallback
    }

    @VisibleForTesting
    internal fun isOverSizeLimit(bitmapSize: Int): Boolean =
        bitmapSize > BITMAP_SIZE_LIMIT_BYTES

    // endregion

    // region testing

    @VisibleForTesting
    internal fun getThreadPoolExecutor(): ExecutorService = threadPoolExecutor

    // endregion

    // region private

    @WorkerThread
    private fun serialiseBitmap(
        bitmap: Bitmap,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe,
        asyncImageProcessingCallback: AsyncImageProcessingCallback?
    ) {
        val base64String = convertBmpToBase64(bitmap)
        finalizeRecordedDataItem(base64String, imageWireframe, asyncImageProcessingCallback)
    }

    @WorkerThread
    private fun convertBmpToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = webPImageCompression.compressBitmapToStream(bitmap)

        if (isOverSizeLimit(byteArrayOutputStream.size())) {
            return ""
        }

        val base64: String
        try {
            base64 = base64Utils.serializeToBase64String(byteArrayOutputStream)
        } finally {
            bitmap.recycle()
        }

        return base64
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
            webPImageCompression: ImageCompression = WebPImageCompression()
        ) =
            Base64Serializer(
                threadPoolExecutor = threadPoolExecutor,
                drawableUtils = drawableUtils,
                base64Utils = base64Utils,
                webPImageCompression = webPImageCompression
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
        @VisibleForTesting
        internal const val BITMAP_SIZE_LIMIT_BYTES = 15000 // 15 kbs
    }
}
