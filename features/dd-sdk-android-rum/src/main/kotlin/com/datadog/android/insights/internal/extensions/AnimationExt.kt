/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.internal.extensions

import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop

private const val ANIMATION_DURATION = 300L

internal fun View.animateVisibility(newVisibilityState: Boolean) {
    scaleX = if (!isVisible) 0f else 1f
    scaleY = if (!isVisible) 0f else 1f
    alpha = if (!isVisible) 0f else 1f
    isVisible = true

    @Suppress("UnsafeThirdPartyFunctionCall") // ANIMATION_DURATION is always >= 0
    newOnlyAnimation()
        .scaleX(1f - scaleX)
        .scaleY(1f - scaleY)
        .alpha(1f - alpha)
        .setDuration(ANIMATION_DURATION)
        .withEndAction { isVisible = newVisibilityState }
        .start()
}

@Suppress("UnsafeThirdPartyFunctionCall")
internal fun View.animateDragTo(x: Float, y: Float) = newOnlyAnimation()
    .x(
        x.clip(
            size = width,
            min = marginStart,
            max = resources.displayMetrics.widthPixels - marginEnd
        )
    )
    .y(
        y.clip(
            size = height,
            min = marginTop,
            max = resources.displayMetrics.heightPixels - marginBottom
        )
    )
    .setDuration(0)
    .start()

internal fun View.newOnlyAnimation() = animate().apply { cancel() }
