/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * Whether a region is eligible for pixel capture under a given [ImagePrivacy] level, shared by
 * every pixel-capture path (a whole View's fallback rasterization, a TextView's non-solid
 * background) so the decision and its placeholder labels stay single-sourced rather than
 * duplicated per call site.
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

    private fun isLarge(boundsDp: GlobalBounds): Boolean =
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

    private const val MAX_CAPTURABLE_AREA_IN_SCREENS = 8L
    private const val MASK_CONTEXTUAL_CONTENT_LABEL = "Content Image"
    private const val MASK_ALL_CONTENT_LABEL = "Image"
}
