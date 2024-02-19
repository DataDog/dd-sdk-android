
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesSerializer
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper
import java.util.concurrent.ExecutorService
import kotlin.math.sqrt

internal class DrawableUtils(
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(),
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(),
    private val bitmapCachesManager: BitmapCachesManager,
    private val threadPoolExecutor: ExecutorService,
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper()),
    private val logger: InternalLogger
) {

    /**
     * This method attempts to create a bitmap from a drawable, such that the bitmap file size will
     * be equal or less than a given size. It does so by modifying the dimensions of the
     * bitmap, since the file size of a bitmap can be known by the formula width*height*color depth
     */
    internal fun createBitmapOfApproxSizeFromDrawable(
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT,
        config: Config = Config.ARGB_8888,
        bitmapCreationCallback: ResourcesSerializer.BitmapCreationCallback
    ) {
        Runnable {
            @Suppress("ThreadSafety") // this runs inside an executor
            createScaledBitmap(
                drawableWidth,
                drawableHeight,
                requestedSizeInBytes,
                displayMetrics,
                config,
                resizeBitmapCallback = object :
                    ResizeBitmapCallback {
                    override fun onSuccess(bitmap: Bitmap) {
                        mainThreadHandler.post {
                            @Suppress("ThreadSafety") // this runs on the main thread
                            drawOnCanvas(
                                bitmap,
                                drawable,
                                bitmapCreationCallback
                            )
                        }
                    }

                    override fun onFailure() {
                        bitmapCreationCallback.onFailure()
                    }
                }
            )
        }.let {
            threadPoolExecutor.executeSafe(
                "createBitmapOfApproxSizeFromDrawable",
                logger,
                it
            )
        }
    }

    @WorkerThread
    internal fun createScaledBitmap(
        bitmap: Bitmap,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT
    ): Bitmap? {
        val (width, height) = getScaledWidthAndHeight(
            bitmap.width,
            bitmap.height,
            requestedSizeInBytes
        )
        return bitmapWrapper.createScaledBitmap(bitmap, width, height, false)
    }

    internal interface ResizeBitmapCallback {
        fun onSuccess(bitmap: Bitmap)
        fun onFailure()
    }

    @MainThread
    private fun drawOnCanvas(
        bitmap: Bitmap,
        drawable: Drawable,
        bitmapCreationCallback: ResourcesSerializer.BitmapCreationCallback
    ) {
        val canvas = canvasWrapper.createCanvas(bitmap)

        if (canvas == null) {
            bitmapCreationCallback.onFailure()
        } else {
            // erase the canvas
            // needed because overdrawing an already used bitmap causes unusual visual artifacts
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmapCreationCallback.onReady(bitmap)
        }
    }

    @WorkerThread
    private fun createScaledBitmap(
        drawableWidth: Int,
        drawableHeight: Int,
        requestedSizeInBytes: Int,
        displayMetrics: DisplayMetrics,
        config: Config,
        resizeBitmapCallback: ResizeBitmapCallback
    ) {
        val (width, height) = getScaledWidthAndHeight(
            drawableWidth,
            drawableHeight,
            requestedSizeInBytes
        )

        val result = getBitmapBySize(displayMetrics, width, height, config)

        if (result == null) {
            resizeBitmapCallback.onFailure()
        } else {
            resizeBitmapCallback.onSuccess(result)
        }
    }

    private fun getScaledWidthAndHeight(
        drawableWidth: Int,
        drawableHeight: Int,
        requestedSizeInBytes: Int
    ): Pair<Int, Int> {
        var width = drawableWidth
        var height = drawableHeight
        val sizeAfterCreation = width * height * ARGB_8888_PIXEL_SIZE_BYTES

        if (sizeAfterCreation > requestedSizeInBytes) {
            val bitmapRatio = width.toDouble() / height.toDouble()
            val totalMaxPixels = (requestedSizeInBytes / ARGB_8888_PIXEL_SIZE_BYTES).toDouble()
            val maxSize = sqrt(totalMaxPixels).toInt()
            width = maxSize
            height = maxSize

            if (bitmapRatio > 1) { // width gt height
                height = (maxSize / bitmapRatio).toInt()
            } else {
                width = (maxSize * bitmapRatio).toInt()
            }
        }

        return Pair(width, height)
    }

    @Suppress("ReturnCount")
    private fun getBitmapBySize(
        displayMetrics: DisplayMetrics,
        width: Int,
        height: Int,
        config: Config
    ): Bitmap? =
        bitmapCachesManager.getBitmapByProperties(width, height, config)
            ?: bitmapWrapper.createBitmap(displayMetrics, width, height, config)

    internal companion object {
        @VisibleForTesting
        internal const val MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT = 10 * 1024 * 1024 // 10mb
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
    }
}
