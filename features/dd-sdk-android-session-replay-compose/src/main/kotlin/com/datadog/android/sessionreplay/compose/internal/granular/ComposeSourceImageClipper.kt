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
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeImageBitmapCache
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
    private data class ClipKey(
        val scaled: GlobalBounds,
        val width: Int,
        val height: Int,
        val radius: Float,
        val density: Float
    )

    private val imageCache = ComposeImageBitmapCache<ClipKey>()

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
        // Scrolling changes absolute placement, not these source-image pixels.
        val key = ClipKey(
            GlobalBounds(
                scaledBounds.x - viewport.x,
                scaledBounds.y - viewport.y,
                scaledBounds.width,
                scaledBounds.height
            ),
            width,
            height,
            radiusDp * density,
            density
        )
        return imageCache.getOrCreate(bitmap, key) { rasterize(bitmap, key) }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Render permitted source pixels into recorder-owned output.
    private fun rasterize(bitmap: Bitmap, key: ClipKey): Bitmap {
        val pixels = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
        var completed = false
        return try {
            val canvas = Canvas(pixels)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawRoundRect(
                RectF(0f, 0f, key.width.toFloat(), key.height.toFloat()),
                key.radius,
                key.radius,
                mask
            )
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            val target = RectF(
                key.scaled.x * key.density,
                key.scaled.y * key.density,
                (key.scaled.x + key.scaled.width) * key.density,
                (key.scaled.y + key.scaled.height) * key.density
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
