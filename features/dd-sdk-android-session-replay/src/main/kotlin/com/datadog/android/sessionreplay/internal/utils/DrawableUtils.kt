
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.recorder.base64.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper
import kotlin.math.sqrt

internal class DrawableUtils(
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(),
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(),
    private val bitmapPool: BitmapPool = BitmapPool
) {
    /**
     * This method attempts to create a bitmap from a drawable, such that the bitmap file size will
     * be equal or less than a given size. It does so by modifying the dimensions of the
     * bitmap, since the file size of a bitmap can be known by the formula width*height*color depth
     */
    @MainThread
    @Suppress("ReturnCount")
    internal fun createBitmapOfApproxSizeFromDrawable(
        applicationContext: Context,
        drawable: Drawable,
        displayMetrics: DisplayMetrics,
        requestedSizeInBytes: Int = MAX_BITMAP_SIZE_IN_BYTES,
        config: Config = Config.ARGB_8888
    ): Bitmap? {
        registerBitmapPoolForCallbacks(applicationContext)

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
        bitmapPool.getBitmapByProperties(width, height, config)
            ?: bitmapWrapper.createBitmap(displayMetrics, width, height, config)

    @MainThread
    private fun registerBitmapPoolForCallbacks(applicationContext: Context) {
        if (isBitmapPoolRegisteredForCallbacks) return

        applicationContext.registerComponentCallbacks(bitmapPool)
        isBitmapPoolRegisteredForCallbacks = true
    }

    internal companion object {
        private const val MAX_BITMAP_SIZE_IN_BYTES = 10240 // 10kb
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4

        // The cache is a singleton, so we want to share this flag among
        // all instances so that it's registered only once
        @VisibleForTesting
        internal var isBitmapPoolRegisteredForCallbacks: Boolean = false
    }
}
