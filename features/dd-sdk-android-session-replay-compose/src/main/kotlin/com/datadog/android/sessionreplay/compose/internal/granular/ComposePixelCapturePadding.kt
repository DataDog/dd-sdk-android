/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedClip

internal data class PaddedComposePixelCapture(
    val bitmap: Bitmap,
    val bounds: CapturedBounds,
    val clip: CapturedClip? = null
)

/** Pixel-free geometry shared by actual resources and early privacy placeholders. */
internal class ComposePixelCaptureGeometry(
    val bounds: CapturedBounds,
    val clip: CapturedClip? = null
) {
    companion object {
        fun intersectsHost(region: Rect, width: Int, height: Int): Boolean =
            width > 0 && height > 0 && region.left.coerceIn(0, width) < region.right.coerceIn(0, width) &&
                region.top.coerceIn(0, height) < region.bottom.coerceIn(0, height)
    }
}

/**
 * Short images can be displaced by the mobile player's default inline-image text baseline.
 * Add transparent rows to the resource and clip the wireframe back to the original capture.
 * Growing both heights by the same integer factor preserves the exact pixel-to-dp scale;
 * no visible pixels move, stretch or come from outside the privacy-checked capture region.
 * The owning layer retains its original bounds.
 */
internal object ComposePixelCapturePadding {
    fun geometry(bounds: CapturedBounds): ComposePixelCaptureGeometry {
        if (bounds.height !in 1 until MIN_IMAGE_HEIGHT_DP) return ComposePixelCaptureGeometry(bounds)
        val factor = (MIN_IMAGE_HEIGHT_DP + bounds.height - 1) / bounds.height
        val paddedBounds = bounds.copy(height = bounds.height * factor)
        return ComposePixelCaptureGeometry(
            paddedBounds,
            CapturedClip(bottom = paddedBounds.height - bounds.height)
        )
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Positive, owned software bitmaps; failures propagate to capture.
    fun pad(bitmap: Bitmap, bounds: CapturedBounds): PaddedComposePixelCapture {
        if (bounds.height !in 1 until MIN_IMAGE_HEIGHT_DP || bitmap.width <= 0 || bitmap.height <= 0) {
            return PaddedComposePixelCapture(bitmap, bounds)
        }
        val geometry = geometry(bounds)
        val factor = (geometry.bounds.height / bounds.height).toInt()
        val padded = Bitmap.createBitmap(bitmap.width, bitmap.height * factor, Bitmap.Config.ARGB_8888)
        var completed = false
        return try {
            padded.density = bitmap.density
            padded.setHasAlpha(true)
            Canvas(padded).drawBitmap(bitmap, 0f, 0f, null)
            val result = PaddedComposePixelCapture(padded, geometry.bounds, geometry.clip)
            completed = true
            result
        } finally {
            if (!completed) padded.recycle()
        }
    }

    // At least the default 16px font size; the expanded canvas is always less than twice this height.
    private const val MIN_IMAGE_HEIGHT_DP = 16L
}
