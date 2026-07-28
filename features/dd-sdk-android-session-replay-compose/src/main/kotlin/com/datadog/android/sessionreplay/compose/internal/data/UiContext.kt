/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import androidx.compose.ui.unit.Density
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper

internal data class UiContext(
    val parentContentColor: String?,
    val density: Float,
    val textAndInputPrivacy: TextAndInputPrivacy,
    val imagePrivacy: ImagePrivacy,
    val isInUserInputLayout: Boolean = false,
    val imageWireframeHelper: ImageWireframeHelper,
    // The Compose root's distance from the screen origin. Baked into every wireframe's bounds by
    // SemanticsUtils.resolveInnerBounds/resolveBackgroundInfo so positions stay screen-absolute
    // even when Compose is embedded in a scrolled/offset host view. See RUM-16362.
    val windowOffset: ComposeWindowOffset = ComposeWindowOffset.NONE
) {
    val composeDensity: Density
        get() = Density(density)
}
