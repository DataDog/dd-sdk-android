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

internal open class TextWireframeMapper(
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
            text = resolveTextValue(view),
            textStyle = resolveTextStyle(view, pixelsDensity),
            textPosition = resolveTextPosition(view, pixelsDensity)
        )
    }

    protected open fun resolveTextValue(textView: TextView): String {
        return textView.text.toString()
    }

    // region Internal

    private fun resolveTextStyle(textView: TextView, pixelsDensity: Float): MobileSegment
    .TextStyle {
        return MobileSegment.TextStyle(
            resolveFontFamily(textView.typeface),
            textView.textSize.toLong().densityNormalized(pixelsDensity),
            colorAndAlphaAsStringHexa(textView.currentTextColor, OPAQUE_AS_HEXA)
        )
    }

    private fun resolveFontFamily(typeface: Typeface): String {
        return when {
            typeface === Typeface.SANS_SERIF -> SANS_SERIF_FAMILY_NAME
            typeface === Typeface.MONOSPACE -> MONOSPACE_FAMILY_NAME
            typeface === Typeface.SERIF -> SERIF_FAMILY_NAME
            else -> SANS_SERIF_FAMILY_NAME
        }
    }

    private fun resolveTextPosition(textView: TextView, pixelsDensity: Float):
        MobileSegment.TextPosition {
        return MobileSegment.TextPosition(
            resolvePadding(textView, pixelsDensity),
            resolveAlignment(textView)
        )
    }

    private fun resolvePadding(textView: TextView, pixelsDensity: Float): MobileSegment.Padding {
        return MobileSegment.Padding(
            top = textView.totalPaddingTop.densityNormalized(pixelsDensity).toLong(),
            bottom = textView.totalPaddingBottom.densityNormalized(pixelsDensity).toLong(),
            left = textView.totalPaddingStart.densityNormalized(pixelsDensity).toLong(),
            right = textView.totalPaddingEnd.densityNormalized(pixelsDensity).toLong()
        )
    }

    private fun resolveAlignment(textView: TextView): MobileSegment.Alignment {
        return when (textView.textAlignment) {
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
            TextView.TEXT_ALIGNMENT_GRAVITY -> resolveAlignmentFromGravity(textView)
            else -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.LEFT,
                vertical = MobileSegment.Vertical.CENTER
            )
        }
    }

    private fun resolveAlignmentFromGravity(textView: TextView): MobileSegment.Alignment {
        val horizontalAlignment = when (textView.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK)) {
            Gravity.START,
            Gravity.LEFT -> MobileSegment.Horizontal.LEFT
            Gravity.END,
            Gravity.RIGHT -> MobileSegment.Horizontal.RIGHT
            Gravity.CENTER -> MobileSegment.Horizontal.CENTER
            Gravity.CENTER_HORIZONTAL -> MobileSegment.Horizontal.CENTER
            else -> MobileSegment.Horizontal.LEFT
        }
        val verticalAlignment = when (textView.gravity.and(Gravity.VERTICAL_GRAVITY_MASK)) {
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
