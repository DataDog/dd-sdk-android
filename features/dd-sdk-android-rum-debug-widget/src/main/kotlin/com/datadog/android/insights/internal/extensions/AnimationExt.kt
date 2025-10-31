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

internal fun View.animateVisibility(
    newVisibilityState: Boolean,
    end: (() -> Unit)? = null
) {
    animate().cancel()

    if (newVisibilityState) {
        if (!isVisible) {
            scaleX = 0.95f
            scaleY = 0.95f
            alpha = 0f
            isVisible = true
        }
        isClickable = false
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                isClickable = true
                end?.invoke()
            }
            .start()
    } else {
        if (!isVisible) {
            end?.invoke()
            return
        }
        isClickable = false
        animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                isVisible = false
                end?.invoke()
            }
            .start()
    }
}

@Suppress("UnsafeThirdPartyFunctionCall") // setDuration() is called with a constant >= 0
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
