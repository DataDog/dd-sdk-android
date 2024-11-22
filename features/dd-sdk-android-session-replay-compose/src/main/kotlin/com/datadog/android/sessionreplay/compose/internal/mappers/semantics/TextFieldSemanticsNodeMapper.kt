/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.resolveAnnotatedString
import com.datadog.android.sessionreplay.compose.internal.utils.transformCapturedText
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class TextFieldSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {
    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        var index = 0
        val shapeWireframe = resolveTextFieldShapeWireframe(parentContext, semanticsNode, index++)
        val editTextWireframe = resolveEditTextWireframe(parentContext, semanticsNode, index)
        return SemanticsWireframe(
            wireframes = listOfNotNull(shapeWireframe, editTextWireframe),
            uiContext = null
        )
    }

    private fun resolveTextFieldShapeWireframe(
        parentContext: UiContext,
        semanticsNode: SemanticsNode,
        index: Int
    ): MobileSegment.Wireframe {
        val color = semanticsUtils.resolveBackgroundColor(semanticsNode)?.let {
            convertColor(it)
        }
        val globalBounds = semanticsUtils.resolveInnerBounds(semanticsNode)
        val shape = semanticsUtils.resolveBackgroundShape(semanticsNode)
        val cornerRadius = shape?.let {
            semanticsUtils.resolveCornerRadius(it, globalBounds, parentContext.composeDensity)
        }
        return MobileSegment.Wireframe.ShapeWireframe(
            id = resolveId(semanticsNode, index),
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            shapeStyle = color?.let {
                MobileSegment.ShapeStyle(
                    backgroundColor = it,
                    cornerRadius = cornerRadius
                )
            }
        )
    }

    private fun resolveEditTextWireframe(
        parentContext: UiContext,
        semanticsNode: SemanticsNode,
        index: Int
    ): MobileSegment.Wireframe? {
        val globalBounds = semanticsUtils.resolveInnerBounds(semanticsNode)
        val editText = resolveEditText(semanticsNode.config)?.let {
            transformCapturedText(it, parentContext.textAndInputPrivacy, true)
        }
        val textLayoutInfo = semanticsUtils.resolveTextLayoutInfo(semanticsNode)
        val textStyle = textLayoutInfo?.let {
            resolveTextLayoutInfoToTextStyle(parentContext, it)
        } ?: defaultTextStyle
        return editText?.let {
            MobileSegment.Wireframe.TextWireframe(
                id = resolveId(semanticsNode, index),
                x = globalBounds.x,
                y = globalBounds.y,
                width = globalBounds.width,
                height = globalBounds.height,
                text = it,
                textStyle = textStyle,
                textPosition = MobileSegment.TextPosition(
                    padding = MobileSegment.Padding(
                        left = (EDIT_TEXT_START_PADDING * parentContext.composeDensity.density).toLong()
                    ),
                    alignment = MobileSegment.Alignment(
                        horizontal = MobileSegment.Horizontal.LEFT,
                        vertical = MobileSegment.Vertical.CENTER
                    )
                )
            )
        }
    }

    private fun resolveEditText(semanticsConfiguration: SemanticsConfiguration): String? {
        return semanticsConfiguration.getOrNull(SemanticsProperties.EditableText)?.let {
            resolveAnnotatedString(it)
        }
    }

    companion object {
        private const val EDIT_TEXT_START_PADDING = 4L
    }
}
