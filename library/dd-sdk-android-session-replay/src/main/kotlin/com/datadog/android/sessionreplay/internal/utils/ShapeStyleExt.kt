/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Locale

private const val FULL_OPACITY_STRING_HEXA = "ff"
private const val FULL_OPACITY_ALPHA = 1f

internal fun MobileSegment.ShapeStyle.hasNonTranslucentColor(): Boolean {
    return this.backgroundColor != null &&
        @Suppress("UnsafeThirdPartyFunctionCall") // takeLast argument is not negative
    this.backgroundColor.takeLast(2).lowercase(Locale.US) == FULL_OPACITY_STRING_HEXA
}

internal fun MobileSegment.ShapeStyle.isFullyOpaque(): Boolean {
    return (this.opacity?.toFloat() ?: 1f) >= FULL_OPACITY_ALPHA
}
