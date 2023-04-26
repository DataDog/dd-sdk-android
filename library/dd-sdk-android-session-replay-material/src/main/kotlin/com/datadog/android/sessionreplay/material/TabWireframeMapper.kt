/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.material.internal.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.TextWireframe
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.TabView

internal open class TabWireframeMapper(
    private val viewUtils: ViewUtils = ViewUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator =
        UniqueIdentifierGenerator,
    internal val textViewMapper: WireframeMapper<TextView, TextWireframe> = TextViewMapper()
) : WireframeMapper<TabLayout.TabView, MobileSegment.Wireframe> {

    override fun map(
        view: TabView,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe> {
        val labelWireframes = findAndResolveLabelWireframes(view, mappingContext)
        if (view.isSelected) {
            val selectedTabIndicatorWireframe = resolveTabIndicatorWireframe(
                view,
                mappingContext.systemInformation,
                labelWireframes.firstOrNull()
            )
            if (selectedTabIndicatorWireframe != null) {
                return labelWireframes + selectedTabIndicatorWireframe
            }
        }
        return labelWireframes
    }

    protected open fun resolveTabIndicatorWireframe(
        view: TabView,
        systemInformation: SystemInformation,
        textWireframe: TextWireframe?
    ): MobileSegment.Wireframe? {
        val selectorId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            SELECTED_TAB_INDICATOR_KEY_NAME
        ) ?: return null
        val screenDensity = systemInformation.screenDensity
        val viewBounds = viewUtils.resolveViewGlobalBounds(view, screenDensity)
        val selectionIndicatorHeight = SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(screenDensity)
        val paddingStart = view.paddingStart.toLong().densityNormalized(screenDensity)
        val paddingEnd = view.paddingEnd.toLong().densityNormalized(screenDensity)
        val selectionIndicatorXPos = viewBounds.x + paddingStart
        val selectionIndicatorYPos = viewBounds.y + viewBounds.height - selectionIndicatorHeight
        val selectionIndicatorWidth = viewBounds.width - paddingStart - paddingEnd
        val selectionIndicatorColor = textWireframe?.textStyle?.color
            ?: SELECTED_TAB_INDICATOR_DEFAULT_COLOR
        val selectionIndicatorShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = selectionIndicatorColor,
            opacity = view.alpha
        )
        return MobileSegment.Wireframe.ShapeWireframe(
            id = selectorId,
            x = selectionIndicatorXPos,
            y = selectionIndicatorYPos,
            width = selectionIndicatorWidth,
            height = selectionIndicatorHeight,
            shapeStyle = selectionIndicatorShapeStyle
        )
    }

    private fun findAndResolveLabelWireframes(view: TabView, mappingContext: MappingContext):
        List<TextWireframe> {
        for (i in 0 until view.childCount) {
            val viewChild = view.getChildAt(i) ?: continue
            if (TextView::class.java.isAssignableFrom(viewChild::class.java)) {
                return textViewMapper.map(viewChild as TextView, mappingContext)
            }
        }
        return emptyList()
    }

    companion object {
        internal const val SELECTED_TAB_INDICATOR_KEY_NAME = "selected_tab_indicator"
        internal const val SELECTED_TAB_INDICATOR_DEFAULT_COLOR = "#000000"
        internal const val SELECTED_TAB_INDICATOR_HEIGHT_IN_PX = 5L
    }
}
