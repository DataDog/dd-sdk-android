/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.NumberPicker
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@RequiresApi(Build.VERSION_CODES.Q)
internal open class NumberPickerMapper(
    stringUtils: StringUtils = StringUtils,
    private val viewUtils: ViewUtils = ViewUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator
) : BasePickerMapper(stringUtils, viewUtils) {

    override fun map(view: NumberPicker, mappingContext: MappingContext):
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
                mappingContext.systemInformation,
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
        val textSize = resolveTextSize(view, screenDensity)
        val labelHeight = textSize * 2
        val paddingStart = resolveDividerPaddingStart(view, screenDensity)
        val paddingEnd = resolveDividerPaddingEnd(view, screenDensity)
        val textColor = resolveSelectedTextColor(view)
        val nextPrevLabelTextColor = colorAndAlphaAsStringHexa(
            view.textColor,
            PARTIALLY_OPAQUE_ALPHA_VALUE
        )
        val padding = resolvePadding(screenDensity)
        val selectedLabelYPos = resolveSelectedLabelYPos(viewGlobalBounds, labelHeight)
        val dividerHeight = resolveDividerHeight(screenDensity)

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
            resolvePrevLabelValue(view),
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
            resolveSelectedLabelValue(view),
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
            resolveNextLabelValue(view),
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

    private fun resolvePrevLabelValue(view: NumberPicker): String {
        return resolveLabelValue(view, view.getPrevIndex())
    }

    private fun resolveNextLabelValue(view: NumberPicker): String {
        return resolveLabelValue(view, view.getNextIndex())
    }

    private fun resolveSelectedLabelValue(view: NumberPicker): String {
        return resolveLabelValue(view, view.value)
    }

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

    private fun resolveLabelValue(numberPicker: NumberPicker, index: Int): String {
        val normalizedIndex = index - numberPicker.minValue
        if (numberPicker.displayedValues != null &&
            numberPicker.displayedValues.size > normalizedIndex
        ) {
            return numberPicker.displayedValues[normalizedIndex]
        }
        return index.toString()
    }
}
