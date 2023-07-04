
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper

internal class DrawableUtils(
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(),
    private val canvasWrapper: CanvasWrapper = CanvasWrapper()
) {
    @MainThread
    @Suppress("ReturnCount")
    internal fun createBitmapFromDrawable(drawable: Drawable, displayMetrics: DisplayMetrics): Bitmap? {
        val bitmapWidth = if (drawable.intrinsicWidth <= 0) 1 else drawable.intrinsicWidth
        val bitmapHeight = if (drawable.intrinsicHeight <= 0) 1 else drawable.intrinsicHeight
        val bitmap = bitmapWrapper.createBitmap(displayMetrics, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            ?: return null

        val canvas = canvasWrapper.createCanvas(bitmap) ?: return null
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
