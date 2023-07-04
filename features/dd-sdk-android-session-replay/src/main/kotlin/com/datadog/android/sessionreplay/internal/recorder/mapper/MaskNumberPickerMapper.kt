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
internal open class MaskNumberPickerMapper(
    stringUtils: StringUtils = StringUtils,
    private val viewUtils: ViewUtils = ViewUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator
) : BasePickerMapper(stringUtils, viewUtils) {

    override fun map(view: NumberPicker, mappingContext: MappingContext):
        List<MobileSegment.Wireframe> {
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

        @Suppress("ComplexCondition")
        if (selectedIndexLabelId != null &&
            topDividerId != null &&
            bottomDividerId != null
        ) {
            return map(
                view,
                mappingContext.systemInformation,
                topDividerId,
                selectedIndexLabelId,
                bottomDividerId
            )
        }

        return emptyList()
    }

    private fun map(
        view: NumberPicker,
        systemInformation: SystemInformation,
        topDividerId: Long,
        selectedIndexLabelId: Long,
        bottomDividerId: Long
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
        val padding = resolvePadding(screenDensity)
        val selectedLabelYPos = resolveSelectedLabelYPos(viewGlobalBounds, labelHeight)
        val dividerHeight = resolveDividerHeight(screenDensity)
        val topDividerYPos = selectedLabelYPos - dividerHeight - padding
        val bottomDividerYPos = selectedLabelYPos + labelHeight + padding
        val dividerWidth = viewGlobalBounds.width - paddingEnd - paddingStart
        val dividerXPos = viewGlobalBounds.x + paddingStart
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
            DEFAULT_MASKED_TEXT_VALUE,
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
        return listOf(
            topDividerWireframe,
            selectedValueLabelWireframe,
            bottomDividerWireframe
        )
    }

    companion object {
        internal const val DEFAULT_MASKED_TEXT_VALUE = "xxx"
    }
}
