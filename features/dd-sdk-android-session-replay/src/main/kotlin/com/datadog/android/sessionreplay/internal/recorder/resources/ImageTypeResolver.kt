/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.VisibleForTesting
import com.datadog.android.internal.utils.densityNormalized

internal class ImageTypeResolver {
    fun isDrawablePII(drawable: Drawable, density: Float): Boolean {
        val isNotGradient = drawable !is GradientDrawable
        val widthDp = drawable.intrinsicWidth.densityNormalized(density)
        val heightDp = drawable.intrinsicHeight.densityNormalized(density)

        return isNotGradient && isPIIByDimensions(widthDp, heightDp)
    }

    fun isPIIByDimensions(width: Int, height: Int): Boolean {
        val isGreaterThan = width >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP || height >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP
        return isGreaterThan
    }

    internal companion object {
        // material design icon size is up to 48x48, but use 100 to match more images
        @VisibleForTesting internal const val IMAGE_DIMEN_CONSIDERED_PII_IN_DP = 100
    }
}
