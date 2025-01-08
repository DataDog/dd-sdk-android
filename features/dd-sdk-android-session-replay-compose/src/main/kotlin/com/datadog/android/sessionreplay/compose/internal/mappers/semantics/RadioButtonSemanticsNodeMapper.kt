/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.ColorUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils.Companion.DEFAULT_COLOR_BLACK
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils.Companion.DEFAULT_COLOR_WHITE
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class RadioButtonSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val colorUtils: ColorUtils = ColorUtils()
) : AbstractSemanticsNodeMapper(
    colorStringFormatter,
    semanticsUtils
) {
    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        val fallbackColor = parentContext.parentContentColor?.takeIf { colorUtils.isDarkColor(it) }?.let {
            DEFAULT_COLOR_WHITE
        } ?: DEFAULT_COLOR_BLACK

        val radioButtonColor = if (isMasked(parentContext)) {
            DEFAULT_COLOR_GRAY
        } else {
            val rawRadioButtonColor = semanticsUtils.resolveRadioButtonColor(semanticsNode)
            rawRadioButtonColor?.let { convertColor(it) }
                ?: fallbackColor
        }

        resolveBoxWireframe(
            semanticsNode = semanticsNode,
            color = radioButtonColor,
            wireframeIndex = 0
        )
            .let { wireframes.add(it) }

        if (!isMasked(parentContext)) {
            resolveDotWireframe(
                semanticsNode = semanticsNode,
                color = radioButtonColor,
                wireframeIndex = 1
            )
                ?.let { wireframes.add(it) }
        }

        return SemanticsWireframe(
            uiContext = null,
            wireframes = wireframes
        )
    }

    private fun resolveBoxWireframe(
        semanticsNode: SemanticsNode,
        wireframeIndex: Int,
        color: String
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
                color = color,
                width = BOX_BORDER_WIDTH
            )
        )
    }

    private fun resolveDotWireframe(
        semanticsNode: SemanticsNode,
        wireframeIndex: Int,
        color: String
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
                    backgroundColor = color,
                    cornerRadius = (globalBounds.width - DOT_PADDING_DP * 2) / 2
                )
            )
        } else {
            null
        }
    }

    private fun isMasked(uiContext: UiContext): Boolean {
        return uiContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
    }

    internal companion object {
        private const val DOT_PADDING_DP = 4

        @VisibleForTesting internal const val DEFAULT_COLOR_GRAY = "#8E8F94"
        private const val BOX_BORDER_WIDTH = 1L
    }
}
