/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal class SwitchSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {
    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val isSwitchOn = isSwitchOn(semanticsNode)
        val globalBounds = resolveBounds(semanticsNode)

        val switchWireframes = if (isSwitchMasked(parentContext)) {
            listOf(
                resolveMaskedWireframes(
                    semanticsNode = semanticsNode,
                    globalBounds = globalBounds,
                    wireframeIndex = 0
                )
            )
        } else {
            val trackWireframe = createTrackWireframe(
                semanticsNode = semanticsNode,
                globalBounds = globalBounds,
                wireframeIndex = 0,
                isSwitchOn = isSwitchOn
            )

            val thumbWireframe = createThumbWireframe(
                semanticsNode = semanticsNode,
                globalBounds = globalBounds,
                wireframeIndex = 1,
                isSwitchOn = isSwitchOn
            )

            listOfNotNull(trackWireframe, thumbWireframe)
        }

        return SemanticsWireframe(
            uiContext = null,
            wireframes = switchWireframes
        )
    }

    private fun createTrackWireframe(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        wireframeIndex: Int,
        isSwitchOn: Boolean
    ): MobileSegment.Wireframe {
        val trackColor = if (isSwitchOn) {
            DEFAULT_COLOR_BLACK
        } else {
            DEFAULT_COLOR_WHITE
        }

        @Suppress("MagicNumber")
        return MobileSegment.Wireframe.ShapeWireframe(
            resolveId(semanticsNode, wireframeIndex),
            x = globalBounds.x,
            y = globalBounds.y + (globalBounds.height / 4),
            width = TRACK_WIDTH_DP,
            height = THUMB_DIAMETER_DP.toLong() / 2,
            shapeStyle = MobileSegment.ShapeStyle(
                cornerRadius = CORNER_RADIUS_DP,
                backgroundColor = trackColor
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )
    }

    private fun createThumbWireframe(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        wireframeIndex: Int,
        isSwitchOn: Boolean
    ): MobileSegment.Wireframe {
        val xPosition = if (!isSwitchOn) {
            globalBounds.x
        } else {
            globalBounds.x + globalBounds.width - THUMB_DIAMETER_DP
        }

        @Suppress("MagicNumber")
        val yPosition = globalBounds.y + (globalBounds.height / 4) - (THUMB_DIAMETER_DP / 4)

        val thumbColor = if (!isSwitchOn) {
            DEFAULT_COLOR_WHITE
        } else {
            DEFAULT_COLOR_BLACK
        }

        return MobileSegment.Wireframe.ShapeWireframe(
            resolveId(semanticsNode, wireframeIndex),
            x = xPosition,
            y = yPosition,
            width = THUMB_DIAMETER_DP.toLong(),
            height = THUMB_DIAMETER_DP.toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                cornerRadius = CORNER_RADIUS_DP,
                backgroundColor = thumbColor
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )
    }

    private fun isSwitchOn(semanticsNode: SemanticsNode): Boolean =
        semanticsNode.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On

    private fun isSwitchMasked(parentContext: UiContext): Boolean =
        parentContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS

    private fun resolveMaskedWireframes(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        wireframeIndex: Int
    ): MobileSegment.Wireframe {
        // TODO RUM-5118: Decide how to display masked, currently use empty track,
        return createTrackWireframe(
            semanticsNode = semanticsNode,
            globalBounds = globalBounds,
            wireframeIndex = wireframeIndex,
            isSwitchOn = false
        )
    }

    internal companion object {
        const val TRACK_WIDTH_DP = 34L
        const val CORNER_RADIUS_DP = 20
        const val THUMB_DIAMETER_DP = 20
        const val BORDER_WIDTH_DP = 1L
        const val DEFAULT_COLOR_BLACK = "#000000"
        const val DEFAULT_COLOR_WHITE = "#FFFFFF"
    }
}
