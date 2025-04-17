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
import android.util.AndroidRuntimeException
import android.util.DisplayMetrics
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import java.util.concurrent.ExecutorService
import kotlin.math.sqrt

internal class DrawableUtils(
    private val internalLogger: InternalLogger,
    private val bitmapCachesManager: BitmapCachesManager,
    private val executorService: ExecutorService,
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(internalLogger),
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(internalLogger)
) {

    /**
     * This method attempts to create a bitmap from a drawable, such that the bitmap file size will
     * be equal or less than a given size. It does so by modifying the dimensions of the
     * bitmap, since the file size of a bitmap can be known by the formula width*height*color depth
     */
    @WorkerThread
    internal fun createBitmapOfApproxSizeFromDrawable(
        drawable: Drawable,
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT,
        config: Config = Config.ARGB_8888,
        bitmapCreationCallback: ResourceResolver.BitmapCreationCallback
    ) {
        createScaledBitmap(
            drawableWidth,
            drawableHeight,
            requestedSizeInBytes,
            displayMetrics,
            config,
            resizeBitmapCallback = object : ResizeBitmapCallback {
                @WorkerThread
                override fun onSuccess(bitmap: Bitmap) {
                    executorService.submitSafe("drawOnCanvas", internalLogger) {
                        drawOnCanvas(
                            bitmap,
                            drawable,
                            bitmapCreationCallback
                        )
                    }
                }

                @WorkerThread
                override fun onFailure() {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { FAILED_TO_CREATE_SCALED_BITMAP_ERROR }
                    )
                    bitmapCreationCallback.onFailure()
                }
            }
        )
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
        @WorkerThread
        fun onSuccess(bitmap: Bitmap)

        @WorkerThread
        fun onFailure()
    }

    @Suppress("TooGenericExceptionCaught")
    @WorkerThread
    private fun drawOnCanvas(
        bitmap: Bitmap,
        drawable: Drawable,
        bitmapCreationCallback: ResourceResolver.BitmapCreationCallback
    ) {
        val canvas = canvasWrapper.createCanvas(bitmap)

        if (canvas == null) {
            bitmapCreationCallback.onFailure()
        } else {
            // erase the canvas
            // needed because overdrawing an already used bitmap causes unusual visual artifacts
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

            drawable.setBounds(0, 0, canvas.width, canvas.height)

            try {
                drawable.draw(canvas)
            } catch (e: IndexOutOfBoundsException) {
                logDrawableDrawException(drawable, e)
                bitmapCreationCallback.onFailure()
                return
            } catch (e: AndroidRuntimeException) {
                logDrawableDrawException(drawable, e)
                bitmapCreationCallback.onFailure()
                return
            }

            bitmapCreationCallback.onReady(bitmap)
        }
    }

    private fun logDrawableDrawException(
        drawable: Drawable,
        runtimeException: RuntimeException
    ) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { "$DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION ${drawable.resolveClassName()}" },
            runtimeException
        )
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
            ?: bitmapWrapper.createBitmap(
                bitmapWidth = width,
                bitmapHeight = height,
                config = config,
                displayMetrics = displayMetrics
            )

    internal companion object {
        @VisibleForTesting
        internal const val MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT = 10 * 1024 * 1024 // 10mb
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
        internal const val FAILED_TO_CREATE_SCALED_BITMAP_ERROR =
            "Failed to create a scaled bitmap from the drawable"
        internal const val DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION =
            "Drawable.draw call finished with an exception. Drawable type is"
    }
}
