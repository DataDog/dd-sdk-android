/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.extensions

import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop

private const val ANIMATION_DURATION = 500L

internal fun View.animateVisibility(newVisibilityState: Boolean) {
    alpha = if (newVisibilityState) 0f else 1f
    isVisible = true

    newSingleAnimation()
        .alpha(1f - alpha)
        .setDuration(ANIMATION_DURATION)
        .withEndAction { isVisible = newVisibilityState }
        .start()
}

internal fun View.animateRotateBy(value: Float) = newSingleAnimation()
    .rotationBy(value)
    .setDuration(ANIMATION_DURATION)
    .start()

internal fun View.animateDragTo(x: Float, y: Float) = newSingleAnimation()
    .x(
        x.clip(
            width,
            marginStart,
            resources.displayMetrics.widthPixels - marginEnd
        )
    )
    .y(
        y.clip(
            height,
            marginTop,
            resources.displayMetrics.heightPixels - marginBottom
        )
    )
    .setDuration(0)
    .start()

internal fun View.newSingleAnimation() = animate().apply { cancel() }