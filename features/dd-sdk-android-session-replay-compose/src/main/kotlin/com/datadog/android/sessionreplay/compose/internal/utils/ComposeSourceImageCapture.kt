/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas

/** Captures only permitted painter source pixels; source ownership always stays with the app. */
internal class ComposeSourceImageCapture {
    private val pixelsCache = ComposeImageBitmapCache<Unit>()

    @Suppress("UnsafeThirdPartyFunctionCall") // Cache only recorder-owned software copies.
    fun copy(source: Bitmap): Bitmap? = pixelsCache.getOrCreate(source, Unit) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Pixels are recorder-owned; unsuccessful output is released.
    fun render(source: Bitmap, alpha: Float, colorFilter: ColorFilter?): Bitmap? {
        val pixels = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        var completed = false
        return try {
            if (!drawBitmapSource(source, pixels, alpha, colorFilter)) return null
            completed = true
            pixels
        } finally {
            if (!completed) pixels.recycle()
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Supported software copy; the temporary source is always recycled.
    private fun drawBitmapSource(
        source: Bitmap,
        pixels: Bitmap,
        alpha: Float,
        colorFilter: androidx.compose.ui.graphics.ColorFilter?
    ): Boolean {
        // ALPHA_8 is directly drawable as a transparent mask; hardware images need a software copy.
        val software = if (source.config ==
            Bitmap.Config.ALPHA_8
        ) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (software == null) return false
        try {
            val paint = Paint().apply {
                this.alpha = (alpha * OPAQUE_ALPHA_VALUE).roundToInt()
                this.colorFilter = colorFilter?.asAndroidColorFilter()
            }
            AndroidCanvas(pixels).drawBitmap(software, null, Rect(0, 0, pixels.width, pixels.height), paint)
            return true
        } finally {
            if (software !== source) software.recycle()
        }
    }

    private companion object {
        const val OPAQUE_ALPHA_VALUE = 255
    }
}
