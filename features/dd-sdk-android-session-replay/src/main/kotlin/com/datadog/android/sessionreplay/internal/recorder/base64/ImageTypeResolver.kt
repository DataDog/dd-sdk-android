/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.VisibleForTesting

internal class ImageTypeResolver {
    fun isDrawablePII(drawable: Drawable, widthInDp: Int, heightInDp: Int): Boolean =
        drawable !is GradientDrawable &&
            (
                widthInDp >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP ||
                    heightInDp >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP
                )

    internal companion object {
        // material design icon size is up to 48x48
        @VisibleForTesting internal const val IMAGE_DIMEN_CONSIDERED_PII_IN_DP = 49
    }
}
