/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.graphics.drawable.ScaleDrawable
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.safeGetDrawable
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * Whether this drawable is a large image: legacy `ImageTypeResolver.isDrawablePII`'s rule, where a
 * gradient is never an image whatever size it reports and anything else is judged by its intrinsic
 * size in dp. What a wrapper holds is judged too, since the size it reports can be smaller than its
 * content's (a layer may declare its own).
 */
internal fun Drawable.isLargeImage(density: Float): Boolean = isLargeImageAt(density, depth = 0)

private fun Drawable.isLargeImageAt(density: Float, depth: Int): Boolean {
    if (this is GradientDrawable) return false
    val intrinsicBoundsDp = GlobalBounds(
        x = 0,
        y = 0,
        width = intrinsicWidth.densityNormalized(density).toLong(),
        height = intrinsicHeight.densityNormalized(density).toLong()
    )
    return PixelCaptureEligibility.isLarge(intrinsicBoundsDp) || holdsLargeImage(density, depth)
}

// A wrapper nested beyond any plausible depth is not followed further, but treated as an image.
private fun Drawable.holdsLargeImage(density: Float, depth: Int): Boolean {
    val wrapped = wrappedDrawables()
    return if (depth >= MAX_WRAPPED_DRAWABLE_DEPTH) {
        wrapped.isNotEmpty()
    } else {
        wrapped.any { it.isLargeImageAt(density, depth + 1) }
    }
}

@Suppress("UnsafeThirdPartyFunctionCall") // Stdlib list builders over the children of an existing drawable.
private fun Drawable.wrappedDrawables(): List<Drawable> = when (this) {
    is LayerDrawable -> (0 until numberOfLayers).mapNotNull { safeGetDrawable(it) }
    is DrawableContainer -> listOfNotNull(current.takeIf { it !== this })
    is InsetDrawable -> listOfNotNull(drawable)
    is ScaleDrawable -> listOfNotNull(drawable)
    is ClipDrawable -> listOfNotNull(drawable)
    is RotateDrawable -> listOfNotNull(drawable)
    else -> emptyList()
}

private const val MAX_WRAPPED_DRAWABLE_DEPTH = 8
