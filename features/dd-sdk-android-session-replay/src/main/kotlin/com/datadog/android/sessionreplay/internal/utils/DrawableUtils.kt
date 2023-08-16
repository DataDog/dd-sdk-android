
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
import android.util.DisplayMetrics
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.recorder.base64.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper
import kotlin.math.sqrt

internal class DrawableUtils(
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(),
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(),
    private val bitmapPool: BitmapPool? = null
) {
    /**
     * This method attempts to create a bitmap from a drawable, such that the bitmap file size will
     * be equal or less than a given size. It does so by modifying the dimensions of the
     * bitmap, since the file size of a bitmap can be known by the formula width*height*color depth
     */
    @MainThread
    @Suppress("ReturnCount")
    internal fun createBitmapOfApproxSizeFromDrawable(
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_IN_BYTES,
        config: Config = Config.ARGB_8888
    ): Bitmap? {
        val (width, height) = getScaledWidthAndHeight(drawable, requestedSizeInBytes)

        val bitmap = getBitmapBySize(displayMetrics, width, height, config) ?: return null
        val canvas = canvasWrapper.createCanvas(bitmap) ?: return null

        // erase the canvas
        // needed because overdrawing an already used bitmap causes unusual visual artifacts
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    internal fun getDrawableScaledDimensions(
        view: ImageView,
        drawable: Drawable,
        density: Float
    ): DrawableDimensions {
        val viewWidth = view.width.densityNormalized(density).toLong()
        val viewHeight = view.height.densityNormalized(density).toLong()
        val drawableWidth = drawable.intrinsicWidth.densityNormalized(density).toLong()
        val drawableHeight = drawable.intrinsicHeight.densityNormalized(density).toLong()

        val scaleType = view.scaleType ?: return DrawableDimensions(
            width = drawableWidth,
            height = drawableHeight
        )

        val scaledDrawableWidth: Long
        val scaledDrawableHeight: Long

        when (scaleType) {
            ScaleType.FIT_START,
            ScaleType.FIT_END,
            ScaleType.FIT_CENTER,
            ScaleType.CENTER_INSIDE,
            ScaleType.CENTER,
            ScaleType.MATRIX -> {
                // TODO: REPLAY-1974 Implement remaining scaletype methods
                scaledDrawableWidth = drawableWidth
                scaledDrawableHeight = drawableHeight
            }
            ScaleType.FIT_XY -> {
                scaledDrawableWidth = viewWidth
                scaledDrawableHeight = viewHeight
            }
            ScaleType.CENTER_CROP -> {
                if (drawableWidth * viewHeight > viewWidth * drawableHeight) {
                    scaledDrawableWidth = viewWidth
                    scaledDrawableHeight = (viewWidth * drawableHeight) / drawableWidth
                } else {
                    scaledDrawableHeight = viewHeight
                    scaledDrawableWidth = (viewHeight * drawableWidth) / drawableHeight
                }
            }
        }

        return DrawableDimensions(scaledDrawableWidth, scaledDrawableHeight)
    }

    private fun getScaledWidthAndHeight(
        drawable: Drawable,
        requestedSizeInBytes: Int
    ): Pair<Int, Int> {
        var width = drawable.intrinsicWidth
        var height = drawable.intrinsicHeight
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
        bitmapPool?.getBitmapByProperties(width, height, config)
            ?: bitmapWrapper.createBitmap(displayMetrics, width, height, config)

    internal companion object {
        private const val MAX_BITMAP_SIZE_IN_BYTES = 10240 // 10kb
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
    }
}
