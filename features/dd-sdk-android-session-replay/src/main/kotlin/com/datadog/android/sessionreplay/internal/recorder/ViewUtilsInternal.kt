/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Display
import android.view.View
import android.view.ViewStub
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.Toolbar
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal class ViewUtilsInternal {

    private val systemViewIds by lazy {
        setOf(android.R.id.navigationBarBackground, android.R.id.statusBarBackground)
    }

    internal fun isNotVisible(view: View): Boolean {
        return !view.isShown || view.width <= 0 || view.height <= 0
    }

    @SuppressLint("RestrictedApi") // ActionBarContextView is public, but has @RestrictTo(LIBRARY_GROUP_PREFIX)
    internal fun isSystemNoise(view: View): Boolean {
        return view.id in systemViewIds || view is ViewStub || view is ActionBarContextView
    }

    internal fun isOnSecondaryDisplay(view: View): Boolean {
        val display = view.display
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display != null &&
                display.displayId != Display.DEFAULT_DISPLAY &&
                display.displayId != Display.INVALID_DISPLAY
        } else {
            display != null && display.displayId != Display.DEFAULT_DISPLAY
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
    internal fun isToolbar(view: View): Boolean {
        return Toolbar::class.java.isAssignableFrom(view::class.java) ||
            android.widget.Toolbar::class.java.isAssignableFrom(view::class.java)
    }

    internal fun resolveDrawableBounds(
        view: View,
        drawable: Drawable,
        pixelsDensity: Float
    ): GlobalBounds {
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)
        val x = coordinates[0].densityNormalized(pixelsDensity).toLong()
        val y = coordinates[1].densityNormalized(pixelsDensity).toLong()
        val width = drawable.intrinsicWidth.densityNormalized(pixelsDensity).toLong()
        val height = drawable.intrinsicHeight.densityNormalized(pixelsDensity).toLong()
        return GlobalBounds(x = x, y = y, height = height, width = width)
    }

    internal fun resolveCompoundDrawableBounds(
        view: View,
        drawable: Drawable,
        pixelsDensity: Float,
        position: DefaultImageWireframeHelper.CompoundDrawablePositions
    ): GlobalBounds {
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)

        val viewXPosition = coordinates[0].densityNormalized(pixelsDensity).toLong()
        val viewYPosition = coordinates[1].densityNormalized(pixelsDensity).toLong()
        val drawableWidth = drawable.intrinsicWidth.densityNormalized(pixelsDensity).toLong()
        val drawableHeight = drawable.intrinsicHeight.densityNormalized(pixelsDensity).toLong()
        val viewWidth = view.width.densityNormalized(pixelsDensity).toLong()
        val viewHeight = view.height.densityNormalized(pixelsDensity).toLong()
        val viewPaddingStart = view.paddingStart.densityNormalized(pixelsDensity).toLong()
        val viewPaddingTop = view.paddingTop.densityNormalized(pixelsDensity).toLong()
        val viewPaddingBottom = view.paddingBottom.densityNormalized(pixelsDensity).toLong()
        val viewPaddingEnd = view.paddingEnd.densityNormalized(pixelsDensity).toLong()
        var xPosition: Long
        var yPosition: Long

        when (position) {
            DefaultImageWireframeHelper.CompoundDrawablePositions.LEFT -> {
                xPosition = viewPaddingStart
                yPosition = getCenterVerticalOffset(viewHeight, drawableHeight)
            }

            DefaultImageWireframeHelper.CompoundDrawablePositions.TOP -> {
                xPosition = getCenterHorizontalOffset(viewWidth, drawableWidth)
                yPosition = viewPaddingTop
            }

            DefaultImageWireframeHelper.CompoundDrawablePositions.RIGHT -> {
                xPosition = viewWidth - (drawableWidth + viewPaddingEnd)
                yPosition = getCenterVerticalOffset(viewHeight, drawableHeight)
            }

            DefaultImageWireframeHelper.CompoundDrawablePositions.BOTTOM -> {
                xPosition = getCenterHorizontalOffset(viewWidth, drawableWidth)
                yPosition = viewHeight - (drawableHeight + viewPaddingBottom)
            }
        }

        xPosition += viewXPosition
        yPosition += viewYPosition

        return GlobalBounds(x = xPosition, y = yPosition, height = drawableHeight, width = drawableWidth)
    }

    private fun getCenterHorizontalOffset(viewWidth: Long, drawableWidth: Long): Long =
        (viewWidth / 2) - (drawableWidth / 2)

    private fun getCenterVerticalOffset(viewHeight: Long, drawableHeight: Long): Long =
        (viewHeight / 2) - (drawableHeight / 2)
}
