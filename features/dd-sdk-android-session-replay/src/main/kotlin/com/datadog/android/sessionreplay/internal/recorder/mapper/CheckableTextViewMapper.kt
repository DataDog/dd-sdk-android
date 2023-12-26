/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.Checkable
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal abstract class CheckableTextViewMapper<T>(
    private val textWireframeMapper: TextViewMapper,
    private val stringUtils: StringUtils = StringUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils
) : CheckableWireframeMapper<T>(viewUtils) where T : TextView, T : Checkable {

    // region CheckableWireframeMapper

    override fun resolveMainWireframes(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        return textWireframeMapper.map(view, mappingContext, asyncJobStatusCallback)
    }

    override fun resolveCheckedCheckable(
        view: T,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        val checkableId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            CHECKABLE_KEY_NAME
        ) ?: return null
        val checkBoxColor = resolveCheckableColor(view)
        val checkBoxBounds = resolveCheckableBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val shapeStyle = resolveCheckedShapeStyle(view, checkBoxColor)
        val shapeBorder = resolveCheckedShapeBorder(view, checkBoxColor)
        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                id = checkableId,
                x = checkBoxBounds.x,
                y = checkBoxBounds.y,
                width = checkBoxBounds.width,
                height = checkBoxBounds.height,
                border = shapeBorder,
                shapeStyle = shapeStyle
            )
        )
    }

    override fun resolveNotCheckedCheckable(
        view: T,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        val checkableId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            CHECKABLE_KEY_NAME
        ) ?: return null
        val checkBoxColor = resolveCheckableColor(view)
        val checkBoxBounds = resolveCheckableBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val shapeBorder = resolveNotCheckedShapeBorder(view, checkBoxColor)
        val shapeStyle = resolveNotCheckedShapeStyle(view, checkBoxColor)
        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                id = checkableId,
                x = checkBoxBounds.x,
                y = checkBoxBounds.y,
                width = checkBoxBounds.width,
                height = checkBoxBounds.height,
                border = shapeBorder,
                shapeStyle = shapeStyle
            )
        )
    }

    // endregion

    // region CheckableTextViewMapper

    abstract fun resolveCheckableBounds(view: T, pixelsDensity: Float): GlobalBounds

    protected open fun resolveCheckableColor(view: T): String {
        return stringUtils.formatColorAndAlphaAsHexa(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    protected open fun resolveCheckedShapeStyle(view: T, checkBoxColor: String): MobileSegment.ShapeStyle? {
        return MobileSegment.ShapeStyle(
            backgroundColor = checkBoxColor,
            view.alpha
        )
    }

    protected open fun resolveCheckedShapeBorder(view: T, checkBoxColor: String): MobileSegment.ShapeBorder? {
        return MobileSegment.ShapeBorder(
            color = checkBoxColor,
            width = CHECKABLE_BORDER_WIDTH
        )
    }

    protected open fun resolveNotCheckedShapeStyle(view: T, checkBoxColor: String): MobileSegment.ShapeStyle? {
        return null
    }

    protected open fun resolveNotCheckedShapeBorder(view: T, checkBoxColor: String): MobileSegment.ShapeBorder? {
        return MobileSegment.ShapeBorder(
            color = checkBoxColor,
            width = CHECKABLE_BORDER_WIDTH
        )
    }

    // endregion

    companion object {
        internal const val CHECKABLE_KEY_NAME = "checkable"
        internal const val CHECKABLE_BORDER_WIDTH = 1L
    }
}
