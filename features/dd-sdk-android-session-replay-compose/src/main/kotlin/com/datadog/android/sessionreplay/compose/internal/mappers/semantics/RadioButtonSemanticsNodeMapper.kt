/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class RadioButtonSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(
    colorStringFormatter,
    semanticsUtils
) {
    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        var wireframeIndex = 0
        val boxWireframe = resolveBoxWireframe(semanticsNode, wireframeIndex++)
        val dotWireframe = resolveDotWireframe(semanticsNode, wireframeIndex)
        return SemanticsWireframe(
            uiContext = null,
            wireframes = listOfNotNull(boxWireframe, dotWireframe)
        )
    }

    private fun resolveBoxWireframe(
        semanticsNode: SemanticsNode,
        wireframeIndex: Int
    ): MobileSegment.Wireframe {
        val globalBounds = resolveBounds(semanticsNode)
        return MobileSegment.Wireframe.ShapeWireframe(
            id = resolveId(semanticsNode, wireframeIndex),
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                cornerRadius = globalBounds.width / 2
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BOX_BORDER_WIDTH
            )
        )
    }

    private fun resolveDotWireframe(
        semanticsNode: SemanticsNode,
        wireframeIndex: Int
    ): MobileSegment.Wireframe? {
        val selected = semanticsNode.config.getOrNull(SemanticsProperties.Selected) ?: false
        val globalBounds = resolveBounds(semanticsNode)
        return if (selected) {
            MobileSegment.Wireframe.ShapeWireframe(
                id = resolveId(semanticsNode, wireframeIndex),
                x = globalBounds.x + DOT_PADDING_DP,
                y = globalBounds.y + DOT_PADDING_DP,
                width = globalBounds.width - DOT_PADDING_DP * 2,
                height = globalBounds.height - DOT_PADDING_DP * 2,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = DEFAULT_COLOR_BLACK,
                    cornerRadius = (globalBounds.width - DOT_PADDING_DP * 2) / 2
                )
            )
        } else {
            null
        }
    }

    companion object {
        private const val DOT_PADDING_DP = 4
        private const val DEFAULT_COLOR_BLACK = "#000000FF"
        private const val BOX_BORDER_WIDTH = 1L
    }
}
