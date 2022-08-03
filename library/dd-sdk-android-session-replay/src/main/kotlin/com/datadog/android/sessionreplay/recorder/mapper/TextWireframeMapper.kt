/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.densityNormalized

internal class TextWireframeMapper(
    private val viewWireframeMapper: ViewWireframeMapper = ViewWireframeMapper()
) : BaseWireframeMapper<TextView, MobileSegment.Wireframe.TextWireframe>() {

    override fun map(view: TextView, pixelsDensity: Float): MobileSegment.Wireframe.TextWireframe {
        val shapeWireframe = viewWireframeMapper.map(view, pixelsDensity)
        return MobileSegment.Wireframe.TextWireframe(
            shapeWireframe.id,
            shapeWireframe.x,
            shapeWireframe.y,
            shapeWireframe.width,
            shapeWireframe.height,
            shapeStyle = shapeWireframe.shapeStyle,
            border = shapeWireframe.border,
            text = view.text.toString(),
            textStyle = view.resolveTextStyle(pixelsDensity),
            textPosition = view.resolveTextPosition(pixelsDensity)
        )
    }

    // region Internal

    private fun TextView.resolveTextStyle(pixelsDensity: Float): MobileSegment.TextStyle {
        return MobileSegment.TextStyle(
            this.typeface.resolveFontFamily(),
            this.textSize.toLong().densityNormalized(pixelsDensity),
            colorAndAlphaAsStringHexa(currentTextColor, OPAQUE_AS_HEXA)
        )
    }

    private fun Typeface.resolveFontFamily(): String {
        return when {
            this === Typeface.SANS_SERIF -> SANS_SERIF_FAMILY_NAME
            this === Typeface.MONOSPACE -> MONOSPACE_FAMILY_NAME
            this === Typeface.SERIF -> SERIF_FAMILY_NAME
            else -> SANS_SERIF_FAMILY_NAME
        }
    }

    private fun TextView.resolveTextPosition(pixelsDensity: Float): MobileSegment.TextPosition {
        return MobileSegment.TextPosition(resolvePadding(pixelsDensity), resolveAlignment())
    }

    private fun TextView.resolvePadding(pixelsDensity: Float): MobileSegment.Padding {
        return MobileSegment.Padding(
            top = this.totalPaddingTop.densityNormalized(pixelsDensity).toLong(),
            bottom = this.totalPaddingBottom.densityNormalized(pixelsDensity).toLong(),
            left = this.totalPaddingStart.densityNormalized(pixelsDensity).toLong(),
            right = this.totalPaddingEnd.densityNormalized(pixelsDensity).toLong()
        )
    }

    private fun TextView.resolveAlignment(): MobileSegment.Alignment {
        return when (this.textAlignment) {
            TextView.TEXT_ALIGNMENT_CENTER -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.CENTER,
                vertical = MobileSegment.Vertical.CENTER
            )
            TextView.TEXT_ALIGNMENT_TEXT_END,
            TextView.TEXT_ALIGNMENT_VIEW_END -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.RIGHT,
                vertical = MobileSegment.Vertical.CENTER
            )
            TextView.TEXT_ALIGNMENT_TEXT_START,
            TextView.TEXT_ALIGNMENT_VIEW_START -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.LEFT,
                vertical = MobileSegment.Vertical.CENTER
            )
            TextView.TEXT_ALIGNMENT_GRAVITY -> resolveAlignmentFromGravity()
            else -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.LEFT,
                vertical = MobileSegment.Vertical.CENTER
            )
        }
    }

    private fun TextView.resolveAlignmentFromGravity(): MobileSegment.Alignment {
        val horizontalAlignment = when (this.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK)) {
            Gravity.START,
            Gravity.LEFT -> MobileSegment.Horizontal.LEFT
            Gravity.END,
            Gravity.RIGHT -> MobileSegment.Horizontal.RIGHT
            Gravity.CENTER -> MobileSegment.Horizontal.CENTER
            Gravity.CENTER_HORIZONTAL -> MobileSegment.Horizontal.CENTER
            else -> MobileSegment.Horizontal.LEFT
        }
        val verticalAlignment = when (this.gravity.and(Gravity.VERTICAL_GRAVITY_MASK)) {
            Gravity.TOP -> MobileSegment.Vertical.TOP
            Gravity.BOTTOM -> MobileSegment.Vertical.BOTTOM
            Gravity.CENTER_VERTICAL -> MobileSegment.Vertical.CENTER
            Gravity.CENTER -> MobileSegment.Vertical.CENTER
            else -> MobileSegment.Vertical.CENTER
        }

        return MobileSegment.Alignment(horizontalAlignment, verticalAlignment)
    }

    // endregion

    companion object {
        internal const val SANS_SERIF_FAMILY_NAME = "sans-serif"
        internal const val SERIF_FAMILY_NAME = "serif"
        internal const val MONOSPACE_FAMILY_NAME = "monospace"
    }
}
