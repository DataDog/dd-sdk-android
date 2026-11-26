/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.datadog.android.internal.system.BuildSdkVersionProvider

/**
 * A software [android.graphics.Canvas] rejects [Bitmap.Config.HARDWARE] bitmaps, which image loaders
 * produce routinely, so a [BitmapDrawable] backed by one is drawn from a copy. Anything else is
 * drawn as is. The copy keeps the bounds, alpha, color filter, gravity, tiling and target density of
 * the original, but not a tint.
 */
internal class SoftwareDrawableResolver(
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) {

    /**
     * [drawable] itself or a software copy of it, or null when it is a hardware bitmap too large to
     * copy within the raster budget.
     */
    @Suppress("ReturnCount")
    fun resolve(drawable: Drawable): Drawable? {
        if (drawable !is BitmapDrawable) return drawable
        val bitmap = drawable.bitmap ?: return drawable
        if (!buildSdkVersionProvider.isAtLeastO || bitmap.config != Bitmap.Config.HARDWARE) return drawable
        if (PixelCaptureEligibility.exceedsRasterBudget(bitmap.width, bitmap.height)) return null

        @Suppress("UnsafeThirdPartyFunctionCall") // An immutable software copy, within the raster budget.
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        return BitmapDrawable(null as Resources?, copy).apply {
            bounds = drawable.copyBounds()
            alpha = drawable.alpha
            colorFilter = drawable.colorFilter
            gravity = drawable.gravity
            setTileModeXY(drawable.tileModeX, drawable.tileModeY)
            targetDensityOf(drawable, copy)?.let { setTargetDensity(it) }
        }
    }

    // Without resources the copy assumes the default density, which would change the size that gravity and
    // tiling draw it at. The original's intrinsic size is the bitmap scaled to its target density, so the
    // density follows from it, measured along the longer side for precision.
    private fun targetDensityOf(original: BitmapDrawable, copy: Bitmap): Int? {
        val bitmapDensity = copy.density
        val isWider = copy.width >= copy.height
        val intrinsicSize = if (isWider) original.intrinsicWidth else original.intrinsicHeight
        val bitmapSize = if (isWider) copy.width else copy.height
        val isDerivable = bitmapDensity != Bitmap.DENSITY_NONE && intrinsicSize > 0 && bitmapSize > 0
        return if (isDerivable) {
            ((intrinsicSize.toLong() * bitmapDensity + bitmapSize / 2) / bitmapSize).toInt().coerceAtLeast(1)
        } else {
            null
        }
    }
}
