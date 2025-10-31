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

private const val ANIMATION_DURATION_MS = 150L
private const val SCALE_HIDDEN = 0.95f
private const val SCALE_VISIBLE = 1f
private const val ALPHA_HIDDEN = 0f
private const val ALPHA_VISIBLE = 1f

internal fun View.animateVisibility(
    newVisibilityState: Boolean,
    end: (() -> Unit)? = null
) {
    animate().cancel()

    if (newVisibilityState) {
        if (!isVisible) {
            scaleX = SCALE_HIDDEN
            scaleY = SCALE_HIDDEN
            alpha = ALPHA_HIDDEN
            isVisible = true
        }
        isClickable = false
        @Suppress("UnsafeThirdPartyFunctionCall") // setDuration() is called with a constant >= 0
        animate()
            .scaleX(SCALE_VISIBLE)
            .scaleY(SCALE_VISIBLE)
            .alpha(ALPHA_VISIBLE)
            .setDuration(ANIMATION_DURATION_MS)
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
        @Suppress("UnsafeThirdPartyFunctionCall") // setDuration() is called with a constant >= 0
        animate()
            .scaleX(SCALE_HIDDEN)
            .scaleY(SCALE_HIDDEN)
            .alpha(ALPHA_HIDDEN)
            .setDuration(ANIMATION_DURATION_MS)
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
