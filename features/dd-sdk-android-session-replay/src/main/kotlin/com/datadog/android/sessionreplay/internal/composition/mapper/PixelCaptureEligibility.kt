/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.drawable.Drawable
import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.utils.GlobalBounds
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Whether a region is eligible for pixel capture under a given [ImagePrivacy] level, shared by
 * the pixel-capture paths so the decision and its placeholder labels stay single-sourced rather
 * than duplicated per call site.
 */
internal object PixelCaptureEligibility {

    /**
     * Null if [imagePrivacy] permits capturing [boundsDp], otherwise the placeholder label to use
     * instead: `MASK_ALL` always placeholders, `MASK_LARGE_ONLY` placeholders only regions at or
     * above [IMAGE_DIMEN_CONSIDERED_PII_IN_DP] in either dimension, `MASK_NONE` never placeholders.
     */
    fun placeholderLabelFor(imagePrivacy: ImagePrivacy, boundsDp: GlobalBounds): String? =
        when (imagePrivacy) {
            ImagePrivacy.MASK_ALL -> MASK_ALL_CONTENT_LABEL
            ImagePrivacy.MASK_LARGE_ONLY -> if (isLarge(boundsDp)) {
                MASK_CONTEXTUAL_CONTENT_LABEL
            } else {
                null
            }
            ImagePrivacy.MASK_NONE -> null
        }

    /**
     * Same as [placeholderLabelFor] for a displayed [drawable], which `MASK_LARGE_ONLY` also
     * placeholders when it is a large image by its intrinsic size, however small it is displayed.
     */
    fun placeholderLabelFor(
        imagePrivacy: ImagePrivacy,
        boundsDp: GlobalBounds,
        drawable: Drawable,
        density: Float
    ): String? = placeholderLabelFor(imagePrivacy, boundsDp)
        ?: MASK_CONTEXTUAL_CONTENT_LABEL.takeIf {
            imagePrivacy == ImagePrivacy.MASK_LARGE_ONLY && drawable.isLargeImage(density)
        }

    /**
     * Same as [placeholderLabelFor] but for a View's own background drawable, never a genuine
     * image/photo: `MASK_LARGE_ONLY`'s size-based "large enough to be suspected PII" heuristic
     * exists for real image content, not decorative chrome, so it never applies here regardless
     * of how large the view is - only `MASK_ALL`'s blanket rule still does. Mirrors legacy
     * `BaseAsyncBackgroundWireframeMapper`'s `usePIIPlaceholder = false` for the same reason.
     */
    fun placeholderLabelForBackground(imagePrivacy: ImagePrivacy): String? =
        when (imagePrivacy) {
            ImagePrivacy.MASK_ALL -> MASK_ALL_CONTENT_LABEL
            ImagePrivacy.MASK_LARGE_ONLY,
            ImagePrivacy.MASK_NONE -> null
        }

    /**
     * Whether [background] must not be captured at all under [imagePrivacy]. It is decorative chrome,
     * so only `MASK_ALL` hides it, and `MASK_LARGE_ONLY`'s size heuristic does not apply - except to a
     * non-gradient drawable with a large intrinsic size, which may be a photo whatever view it backs:
     * it is judged by its own size, as an ordinary image is. A photo can sit inside a state list,
     * inset, layer or similar wrapper, whose own size can differ from its content's (a layer may declare
     * a smaller one), so what a wrapper holds is judged as well. Drawables with no intrinsic size, such
     * as colors, shapes and gradients, stay exempt.
     */
    fun isBackgroundMasked(imagePrivacy: ImagePrivacy, background: Drawable, density: Float): Boolean =
        when (imagePrivacy) {
            ImagePrivacy.MASK_ALL -> true
            ImagePrivacy.MASK_LARGE_ONLY -> background.isLargeImage(density)
            ImagePrivacy.MASK_NONE -> false
        }

    /** Whether [boundsDp] is at or above [IMAGE_DIMEN_CONSIDERED_PII_IN_DP] in either dimension. */
    fun isLarge(boundsDp: GlobalBounds): Boolean =
        boundsDp.width >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP || boundsDp.height >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP

    /**
     * Pre-emptive OOM defense: unbounded content could otherwise demand a huge bitmap.
     * [candidateAreaPx]/[screenAreaPx] are both in raw pixels, not dp, so the comparison is
     * resolution-independent.
     */
    fun isTooLargeToCapture(candidateAreaPx: Long, screenAreaPx: Long): Boolean {
        if (screenAreaPx <= 0) return false
        return candidateAreaPx > MAX_CAPTURABLE_AREA_IN_SCREENS * screenAreaPx
    }

    /** Whether an `ARGB_8888` raster of [widthPx] x [heightPx] is over [MAX_RASTER_BYTES]. */
    fun exceedsRasterBudget(widthPx: Int, heightPx: Int): Boolean =
        widthPx.toLong() * heightPx * ARGB_8888_BYTES_PER_PIXEL > MAX_RASTER_BYTES

    /**
     * The size to draw a [widthPx] x [heightPx] region at: unchanged within [MAX_RASTER_BYTES],
     * otherwise scaled down with its aspect ratio kept. The wireframe keeps the region's displayed
     * bounds, so a smaller raster only costs resolution, never placement.
     */
    fun rasterSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        if (!exceedsRasterBudget(widthPx, heightPx)) return widthPx to heightPx
        val scale = sqrt(MAX_RASTER_BYTES.toDouble() / (widthPx.toLong() * heightPx * ARGB_8888_BYTES_PER_PIXEL))
        return max(1, (widthPx * scale).toInt()) to max(1, (heightPx * scale).toInt())
    }

    // The budget legacy `DrawableUtils` scales every drawable raster to.
    private const val MAX_RASTER_BYTES = 10L * 1024 * 1024
    private const val ARGB_8888_BYTES_PER_PIXEL = 4
    private const val MAX_CAPTURABLE_AREA_IN_SCREENS = 8L
    private const val MASK_CONTEXTUAL_CONTENT_LABEL = "Content Image"
    private const val MASK_ALL_CONTENT_LABEL = "Image"
}
