/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.datadog.android.sessionreplay.utils.GlobalBounds
import kotlin.math.roundToInt

/** Operates only on an image source already permitted by image privacy; never reads host pixels. */
internal fun interface ComposeSourceImageClipper {
    fun clip(
        bitmap: Bitmap,
        scaledBounds: GlobalBounds,
        viewport: GlobalBounds,
        radiusDp: Float,
        density: Float
    ): Bitmap?
}

internal class DefaultComposeSourceImageClipper : ComposeSourceImageClipper {
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun clip(
        bitmap: Bitmap,
        scaledBounds: GlobalBounds,
        viewport: GlobalBounds,
        radiusDp: Float,
        density: Float
    ): Bitmap? {
        val validDensity = density.isFinite() && density > 0f
        val validRadius = radiusDp.isFinite() && radiusDp > 0f
        if (!validDensity || !validRadius) return null
        val width = (viewport.width * density).roundToInt()
        val height = (viewport.height * density).roundToInt()
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_IMAGE_PIXELS) return null
        val pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var completed = false
        return try {
            val canvas = Canvas(pixels)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = radiusDp * density
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, mask)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            val target = RectF(
                (scaledBounds.x - viewport.x) * density,
                (scaledBounds.y - viewport.y) * density,
                (scaledBounds.x + scaledBounds.width - viewport.x) * density,
                (scaledBounds.y + scaledBounds.height - viewport.y) * density
            )
            canvas.drawBitmap(bitmap, null, target, paint)
            completed = true
            pixels
        } finally {
            if (!completed) pixels.recycle()
        }
    }

    private companion object {
        const val MAX_IMAGE_PIXELS = 4_000_000L
    }
}
