/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import kotlin.math.sqrt

internal class DrawableUtils(
    private val internalLogger: InternalLogger,
    private val bitmapCachesManager: BitmapCachesManager,
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
                // Drawn right away on this worker: a queued drawing would keep the drawable copy, and with it the
                // source pixels, alive while it waited.
                @WorkerThread
                override fun onSuccess(bitmap: Bitmap) {
                    drawOnCanvas(bitmap, drawable, bitmapCreationCallback)
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

    // Draws a software bitmap scaled to the drawing limit straight into a new ARGB_8888 bitmap, so no scaled copy in
    // the source's own format is made on the way. A hardware bitmap cannot be drawn this way and is read back first.
    @AnyThread
    internal fun createScaledArgb8888Bitmap(bitmap: Bitmap): Bitmap? {
        val (width, height) = getUniformlyScaledSize(bitmap.width, bitmap.height)
        val scaledBitmap = bitmapWrapper.createBitmap(width, height, Config.ARGB_8888) ?: return null
        val isDrawn = canvasWrapper.createCanvas(scaledBitmap)?.let { canvas ->
            drawScaled(canvas, bitmap, width, height)
        } ?: false
        return if (isDrawn) {
            scaledBitmap
        } else {
            scaledBitmap.recycle()
            null
        }
    }

    internal interface ResizeBitmapCallback {
        @WorkerThread
        fun onSuccess(bitmap: Bitmap)

        @WorkerThread
        fun onFailure()
    }

    @Suppress("TooGenericExceptionCaught") // Drawing a recycled bitmap throws a RuntimeException.
    private fun drawScaled(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int): Boolean {
        return try {
            canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
            true
        } catch (e: RuntimeException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_SCALED_BITMAP_ERROR },
                e,
                onlyOnce = true
            )
            false
        }
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
            failDrawing(bitmap, bitmapCreationCallback)
        } else {
            // erase the canvas
            // needed because overdrawing an already used bitmap causes unusual visual artifacts
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            try {
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            } catch (e: RuntimeException) {
                logDrawableDrawException(drawable, e)
                failDrawing(bitmap, bitmapCreationCallback)
                return
            }

            bitmapCreationCallback.onReady(bitmap)
        }
    }

    // The raster goes back to the pool, which reuses or releases it, before the capture fails and returns its budget.
    @WorkerThread
    private fun failDrawing(bitmap: Bitmap, bitmapCreationCallback: ResourceResolver.BitmapCreationCallback) {
        bitmapCachesManager.putInBitmapPool(bitmap)
        bitmapCreationCallback.onFailure()
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

    // Scales both edges by one factor, so the copy keeps the aspect ratio and fills the drawing limit, while keeping
    // each edge within the WebP encoder's limit.
    private fun getUniformlyScaledSize(width: Int, height: Int): Pair<Int, Int> {
        val maxPixels = (MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT / ARGB_8888_PIXEL_SIZE_BYTES).toDouble()
        val scale = minOf(
            minOf(1.0, sqrt(maxPixels / (width.toDouble() * height))),
            MAX_WEBP_DIMENSION.toDouble() / maxOf(width, height)
        )
        return Pair((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
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

            // The long edge stays capped, which also keeps it within the WebP encoder's limits, so the short edge of
            // a very long image is kept at one pixel instead of rounding to zero.
            if (bitmapRatio > 1) { // width gt height
                height = (maxSize / bitmapRatio).toInt().coerceAtLeast(1)
            } else {
                width = (maxSize * bitmapRatio).toInt().coerceAtLeast(1)
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
        internal const val MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT = 10 * 1024 * 1024 // 10mb
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
        internal const val MAX_WEBP_DIMENSION = 16383
        internal const val FAILED_TO_CREATE_SCALED_BITMAP_ERROR =
            "Failed to create a scaled bitmap from the drawable"
        internal const val DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION =
            "Drawable.draw call finished with an exception. Drawable type is"
    }
}
