/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * Privacy gate for `PixelCaptureFallbackMapper`'s raw pixel capture of unmapped views.
 *
 * This pipeline captures raw pixels with no knowledge of what a given view actually
 * renders — unlike the semantic mapper chain, it can't selectively mask only
 * sensitive text or contextual images. Any privacy restriction beyond what's allowed here
 * disables pixel capture entirely for that region rather than risk uploading unmasked
 * sensitive content:
 *
 * - [textAndInputPrivacy] must be [TextAndInputPrivacy.MASK_SENSITIVE_INPUTS] (its
 *   baseline — [TextAndInputPrivacy] has no fully-permissive "off" state); anything
 *   stricter disables pixel capture unconditionally.
 * - [imagePrivacy] gates on [boundsDp], mirroring how [ImagePrivacy.MASK_LARGE_ONLY]
 *   already behaves for regular images elsewhere in SR: [ImagePrivacy.MASK_NONE] always
 *   allows capture; [ImagePrivacy.MASK_LARGE_ONLY] allows it only when the region is
 *   smaller than [IMAGE_DIMEN_CONSIDERED_PII_IN_DP] on both axes (small regions are
 *   unlikely to be meaningful content); [ImagePrivacy.MASK_ALL] never allows it.
 */
object PixelCaptureEligibility {

    /**
     * Returns whether raw pixel capture is allowed for a region of size [boundsDp] under
     * the given privacy settings.
     */
    fun isEligible(
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        boundsDp: GlobalBounds
    ): Boolean {
        if (textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) return false
        if (imagePrivacy == ImagePrivacy.MASK_ALL) return false

        val isLarge = boundsDp.width >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP ||
            boundsDp.height >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP
        if (imagePrivacy == ImagePrivacy.MASK_LARGE_ONLY && isLarge) return false

        return true
    }
}
