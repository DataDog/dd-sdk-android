
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
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.recorder.base64.BitmapPool
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
        drawableWidth: Int,
        drawableHeight: Int,
        displayMetrics: DisplayMetrics,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_IN_BYTES,
        config: Config = Config.ARGB_8888
    ): Bitmap? {
        val (width, height) = getScaledWidthAndHeight(
            drawableWidth,
            drawableHeight,
            requestedSizeInBytes
        )

        val bitmap = getBitmapBySize(displayMetrics, width, height, config) ?: return null
        val canvas = canvasWrapper.createCanvas(bitmap) ?: return null

        // erase the canvas
        // needed because overdrawing an already used bitmap causes unusual visual artifacts
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @MainThread
    internal fun createScaledBitmap(
        bitmap: Bitmap,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_IN_BYTES
    ): Bitmap? {
        val (width, height) = getScaledWidthAndHeight(
            bitmap.width,
            bitmap.height,
            requestedSizeInBytes
        )
        return bitmapWrapper.createScaledBitmap(bitmap, width, height, false)
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
        bitmapPool?.getBitmapByProperties(width, height, config)
            ?: bitmapWrapper.createBitmap(displayMetrics, width, height, config)

    internal companion object {
        private const val MAX_BITMAP_SIZE_IN_BYTES = 15000 // 15kb
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
    }
}
