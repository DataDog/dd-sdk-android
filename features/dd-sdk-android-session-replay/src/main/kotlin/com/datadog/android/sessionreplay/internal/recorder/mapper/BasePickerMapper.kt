/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.NumberPicker
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

@RequiresApi(Build.VERSION_CODES.Q)
internal abstract class BasePickerMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<NumberPicker, MobileSegment.Wireframe>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    protected fun resolveTextSize(view: NumberPicker, screenDensity: Float): Long {
        return view.textSize.toLong().densityNormalized(screenDensity)
    }

    protected fun resolvePadding(screenDensity: Float): Long {
        return PADDING_IN_PX.densityNormalized(screenDensity)
    }

    protected fun resolveDividerPaddingStart(view: NumberPicker, screenDensity: Float): Long {
        return view.paddingStart.toLong().densityNormalized(screenDensity)
    }

    protected fun resolveDividerPaddingEnd(view: NumberPicker, screenDensity: Float): Long {
        return view.paddingEnd.toLong().densityNormalized(screenDensity)
    }

    protected fun resolveDividerHeight(screenDensity: Float): Long {
        return DIVIDER_HEIGHT_IN_PX.densityNormalized(screenDensity)
    }

    protected fun resolveSelectedLabelYPos(
        viewGlobalBounds: GlobalBounds,
        labelHeight: Long
    ): Long {
        return viewGlobalBounds.y + (viewGlobalBounds.height - labelHeight) / 2
    }

    protected fun resolveSelectedTextColor(view: NumberPicker): String {
        return colorStringFormatter.formatColorAndAlphaAsHexString(view.textColor, OPAQUE_ALPHA_VALUE)
    }

    @Suppress("LongParameterList")
    protected fun provideLabelWireframe(
        id: Long,
        x: Long,
        y: Long,
        height: Long,
        width: Long,
        labelValue: String,
        textSize: Long,
        textColor: String
    ) =
        MobileSegment.Wireframe.TextWireframe(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            text = labelValue,
            textStyle = MobileSegment.TextStyle(
                family = FONT_FAMILY,
                size = textSize,
                color = textColor
            ),
            textPosition = MobileSegment.TextPosition(
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.CENTER,
                    vertical = MobileSegment.Vertical.CENTER
                )
            )
        )

    @Suppress("LongParameterList")
    protected fun provideDividerWireframe(
        id: Long,
        x: Long,
        y: Long,
        width: Long,
        height: Long,
        color: String
    ) =
        MobileSegment.Wireframe.ShapeWireframe(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = color
            )
        )

    companion object {
        internal const val PARTIALLY_OPAQUE_ALPHA_VALUE: Int = 0x44
        internal const val PREV_INDEX_KEY_NAME = "numeric_picker_prev_index"
        internal const val SELECTED_INDEX_KEY_NAME = "numeric_picker_selected_index"
        internal const val NEXT_INDEX_KEY_NAME = "numeric_picker_next_index"
        internal const val DIVIDER_TOP_KEY_NAME = "numeric_picker_divider_top"
        internal const val DIVIDER_BOTTOM_KEY_NAME = "numeric_picker_divider_bottom"
        internal const val DIVIDER_HEIGHT_IN_PX = 6L
        internal const val PADDING_IN_PX = 10L
        internal const val FONT_FAMILY = "Roboto, sans-serif"
    }
}
