/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView

object ImageViewUtils {
    fun resolveParentRectAbsPosition(view: View): Rect {
        val coords = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coords)
        val leftPadding = view.paddingLeft
        val rightPadding = view.paddingRight
        val topPadding = view.paddingTop
        val bottomPadding = view.paddingBottom
        return Rect(
            coords[0] + leftPadding,
            coords[1] + topPadding,
            coords[0] + view.width - rightPadding,
            coords[1] + view.height - bottomPadding
        )
    }

    fun calculateClipping(parentRect: Rect, childRect: Rect, density: Float): Rect {
        val left = if (childRect.left < parentRect.left) {
            parentRect.left - childRect.left
        } else {
            0
        }
        val top = if (childRect.top < parentRect.top) {
            parentRect.top - childRect.top
        } else {
            0
        }
        val right = if (childRect.right > parentRect.right) {
            childRect.right - parentRect.right
        } else {
            0
        }
        val bottom = if (childRect.bottom > parentRect.bottom) {
            childRect.bottom - parentRect.bottom
        } else {
            0
        }
        return Rect(
            left.densityNormalized(density),
            top.densityNormalized(density),
            right.densityNormalized(density),
            bottom.densityNormalized(density)
        )
    }

    fun resolveContentRectWithScaling(
        imageView: ImageView,
        drawable: Drawable
    ): Rect {
        val drawableWidthPx = drawable.intrinsicWidth
        val drawableHeightPx = drawable.intrinsicHeight

        val parentRect = resolveParentRectAbsPosition(imageView)

        val childRect = Rect(
            0,
            0,
            drawableWidthPx,
            drawableHeightPx
        )

        val resultRect: Rect

        when (imageView.scaleType) {
            ImageView.ScaleType.FIT_START -> {
                val contentRect = scaleRectToFitParent(parentRect, childRect)
                resultRect = positionRectAtStart(parentRect, contentRect)
            }
            ImageView.ScaleType.FIT_END -> {
                val contentRect = scaleRectToFitParent(parentRect, childRect)
                resultRect = positionRectAtEnd(parentRect, contentRect)
            }
            ImageView.ScaleType.FIT_CENTER -> {
                val contentRect = scaleRectToFitParent(parentRect, childRect)
                resultRect = positionRectInCenter(parentRect, contentRect)
            }
            ImageView.ScaleType.CENTER_INSIDE -> {
                val contentRect = scaleRectToCenterInsideParent(parentRect, childRect)
                resultRect = positionRectInCenter(parentRect, contentRect)
            }
            ImageView.ScaleType.CENTER -> {
                resultRect = positionRectInCenter(parentRect, childRect)
            }
            ImageView.ScaleType.CENTER_CROP -> {
                val contentRect = scaleRectToCenterCrop(parentRect, childRect)
                resultRect = positionRectInCenter(parentRect, contentRect)
            }
            ImageView.ScaleType.FIT_XY,
            ImageView.ScaleType.MATRIX,
            null -> {
                resultRect = Rect(
                    parentRect.left,
                    parentRect.top,
                    parentRect.right,
                    parentRect.bottom
                )
            }
        }

        return resultRect
    }

    private fun scaleRectToCenterInsideParent(
        parentRect: Rect,
        childRect: Rect
    ): Rect {
        // it already fits inside the parent
        if (parentRect.width() > childRect.width() && parentRect.height() > childRect.height()) {
            return childRect
        }

        val scaleX: Float = parentRect.width().toFloat() / childRect.width().toFloat()
        val scaleY: Float = parentRect.height().toFloat() / childRect.height().toFloat()

        var scaleFactor: Float = minOf(scaleX, scaleY)

        // center inside doesn't enlarge, it only reduces
        if (scaleFactor >= 1F) scaleFactor = 1F

        val newWidth = childRect.width() * scaleFactor
        val newHeight = childRect.height() * scaleFactor

        val resultRect = Rect()
        resultRect.left = parentRect.left
        resultRect.top = parentRect.top
        resultRect.right = resultRect.left + newWidth.toInt()
        resultRect.bottom = resultRect.top + newHeight.toInt()
        return resultRect
    }

    private fun scaleRectToCenterCrop(
        parentRect: Rect,
        childRect: Rect
    ): Rect {
        val scaleX: Float = parentRect.width().toFloat() / childRect.width().toFloat()
        val scaleY: Float = parentRect.height().toFloat() / childRect.height().toFloat()
        val scaleFactor = maxOf(scaleX, scaleY)

        val newWidth = childRect.width() * scaleFactor
        val newHeight = childRect.height() * scaleFactor

        val resultRect = Rect()
        resultRect.left = 0
        resultRect.top = 0
        resultRect.right = newWidth.toInt()
        resultRect.bottom = newHeight.toInt()
        return resultRect
    }

    private fun scaleRectToFitParent(
        parentRect: Rect,
        childRect: Rect
    ): Rect {
        val scaleX: Float = parentRect.width().toFloat() / childRect.width().toFloat()
        val scaleY: Float = parentRect.height().toFloat() / childRect.height().toFloat()
        val scaleFactor = minOf(scaleX, scaleY)

        val newWidth = childRect.width() * scaleFactor
        val newHeight = childRect.height() * scaleFactor

        val resultRect = Rect()
        resultRect.left = 0
        resultRect.top = 0
        resultRect.right = newWidth.toInt()
        resultRect.bottom = newHeight.toInt()
        return resultRect
    }

    private fun positionRectInCenter(parentRect: Rect, childRect: Rect): Rect {
        val centerXParentPx = parentRect.centerX()
        val centerYParentPx = parentRect.centerY()
        val childRectWidthPx = childRect.width()
        val childRectHeightPx = childRect.height()

        val resultRect = Rect()
        resultRect.left = centerXParentPx - (childRectWidthPx / 2)
        resultRect.top = centerYParentPx - (childRectHeightPx / 2)
        resultRect.right = resultRect.left + childRectWidthPx
        resultRect.bottom = resultRect.top + childRectHeightPx
        return resultRect
    }

    private fun positionRectAtStart(parentRect: Rect, childRect: Rect): Rect {
        val childRectWidthPx = childRect.width()
        val childRectHeightPx = childRect.height()

        val resultRect = Rect()
        resultRect.left = parentRect.left
        resultRect.top = parentRect.top
        resultRect.right = resultRect.left + childRectWidthPx
        resultRect.bottom = resultRect.top + childRectHeightPx
        return resultRect
    }

    private fun positionRectAtEnd(parentRect: Rect, childRect: Rect): Rect {
        val childRectWidthPx = childRect.width()
        val childRectHeightPx = childRect.height()

        val resultRect = Rect()
        resultRect.right = parentRect.right
        resultRect.bottom = parentRect.bottom
        resultRect.left = parentRect.right - childRectWidthPx
        resultRect.top = parentRect.bottom - childRectHeightPx
        return resultRect
    }
}
