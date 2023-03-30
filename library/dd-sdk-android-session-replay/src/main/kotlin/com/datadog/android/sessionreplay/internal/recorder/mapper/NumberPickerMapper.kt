/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.NumberPicker
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@RequiresApi(Build.VERSION_CODES.Q)
internal open class NumberPickerMapper(
    stringUtils: StringUtils = StringUtils,
    private val viewUtils: ViewUtils = ViewUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator
) : BaseWireframeMapper<NumberPicker, MobileSegment.Wireframe>(stringUtils, viewUtils) {

    override fun map(view: NumberPicker, systemInformation: SystemInformation):
        List<MobileSegment.Wireframe> {
        val prevIndexLabelId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            PREV_INDEX_KEY_NAME
        )
        val selectedIndexLabelId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            SELECTED_INDEX_KEY_NAME
        )
        val topDividerId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            DIVIDER_TOP_KEY_NAME
        )
        val bottomDividerId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            DIVIDER_BOTTOM_KEY_NAME
        )
        val nextIndexLabelId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            NEXT_INDEX_KEY_NAME
        )

        @Suppress("ComplexCondition")
        if (selectedIndexLabelId != null &&
            topDividerId != null &&
            bottomDividerId != null &&
            prevIndexLabelId != null &&
            nextIndexLabelId != null
        ) {
            return map(
                view,
                systemInformation,
                prevIndexLabelId,
                topDividerId,
                selectedIndexLabelId,
                bottomDividerId,
                nextIndexLabelId
            )
        }

        return emptyList()
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun map(
        view: NumberPicker,
        systemInformation: SystemInformation,
        prevIndexLabelId: Long,
        topDividerId: Long,
        selectedIndexLabelId: Long,
        bottomDividerId: Long,
        nextIndexLabelId: Long
    ): List<MobileSegment.Wireframe> {
        val screenDensity = systemInformation.screenDensity
        val viewGlobalBounds = viewUtils.resolveViewGlobalBounds(
            view,
            screenDensity
        )
        val currentIndex = view.value
        val prevIndex = view.getPrevIndex()
        val nextIndex = view.getNextIndex()
        val textSize = view.textSize.toLong().densityNormalized(screenDensity)
        val labelHeight = textSize * 2
        val paddingStart = view.paddingStart.densityNormalized(screenDensity)
        val paddingEnd = view.paddingEnd.densityNormalized(screenDensity)
        val textColor = colorAndAlphaAsStringHexa(view.textColor, OPAQUE_ALPHA_VALUE)
        val nextPrevLabelTextColor = colorAndAlphaAsStringHexa(
            view.textColor,
            PARTIALLY_OPAQUE_ALPHA_VALUE
        )
        val padding = PADDING_IN_PX.densityNormalized(screenDensity)
        val selectedLabelYPos = viewGlobalBounds.y + (viewGlobalBounds.height - labelHeight) / 2
        val dividerHeight = DIVIDER_HEIGHT_IN_PX
            .densityNormalized(screenDensity)
        val topDividerYPos = selectedLabelYPos - dividerHeight - padding
        val bottomDividerYPos = selectedLabelYPos + labelHeight + padding
        val prevLabelYPos = topDividerYPos - labelHeight - padding
        val nextLabelYPos = bottomDividerYPos + padding
        val dividerWidth = viewGlobalBounds.width - paddingEnd - paddingStart
        val dividerXPos = viewGlobalBounds.x + paddingStart
        val prevValueLabelWireframe = provideLabelWireframe(
            prevIndexLabelId,
            viewGlobalBounds.x,
            prevLabelYPos,
            labelHeight,
            viewGlobalBounds.width,
            resolveLabelValue(view, prevIndex),
            textSize,
            nextPrevLabelTextColor
        )
        val topDividerWireframe = provideDividerWireframe(
            topDividerId,
            dividerXPos,
            topDividerYPos,
            dividerWidth,
            dividerHeight,
            textColor
        )
        val selectedValueLabelWireframe = provideLabelWireframe(
            selectedIndexLabelId,
            viewGlobalBounds.x,
            selectedLabelYPos,
            labelHeight,
            viewGlobalBounds.width,
            resolveLabelValue(view, currentIndex),
            textSize,
            textColor
        )
        val bottomDividerWireframe = provideDividerWireframe(
            bottomDividerId,
            dividerXPos,
            bottomDividerYPos,
            dividerWidth,
            dividerHeight,
            textColor
        )
        val nextValueLabelWireframe = provideLabelWireframe(
            nextIndexLabelId,
            viewGlobalBounds.x,
            nextLabelYPos,
            labelHeight,
            viewGlobalBounds.width,
            resolveLabelValue(view, nextIndex),
            textSize,
            nextPrevLabelTextColor
        )
        return listOf(
            prevValueLabelWireframe,
            topDividerWireframe,
            selectedValueLabelWireframe,
            bottomDividerWireframe,
            nextValueLabelWireframe
        )
    }

    @Suppress("LongParameterList")
    private fun provideLabelWireframe(
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
    private fun provideDividerWireframe(
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

    private fun NumberPicker.getPrevIndex(): Int {
        return if (value > minValue) {
            value - 1
        } else {
            maxValue
        }
    }

    private fun NumberPicker.getNextIndex(): Int {
        return if (value < maxValue) {
            value + 1
        } else {
            minValue
        }
    }

    protected open fun resolveLabelValue(numberPicker: NumberPicker, index: Int): String {
        val normalizedIndex = index - numberPicker.minValue
        if (numberPicker.displayedValues != null &&
            numberPicker.displayedValues.size > normalizedIndex
        ) {
            return numberPicker.displayedValues[normalizedIndex]
        }
        return index.toString()
    }

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
