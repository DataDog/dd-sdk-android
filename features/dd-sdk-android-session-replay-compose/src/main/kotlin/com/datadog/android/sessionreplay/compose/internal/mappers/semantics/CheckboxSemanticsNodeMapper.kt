/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright CHECKBOX_SIZE16-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.PathUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal class CheckboxSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val pathUtils: PathUtils = PathUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val globalBounds = resolveBounds(semanticsNode)

        val wireframes = if (isCheckboxChecked(semanticsNode)) {
            createCheckedWireframes(
                parentContext = parentContext,
                asyncJobStatusCallback = asyncJobStatusCallback,
                semanticsNode = semanticsNode,
                globalBounds = globalBounds
            )
        } else {
            createUncheckedWireframes(
                semanticsNode = semanticsNode,
                globalBounds = globalBounds,
                backgroundColor = DEFAULT_COLOR_WHITE
            )
        }

        return SemanticsWireframe(
            uiContext = null,
            wireframes = wireframes
        )
    }

    private fun createCheckedWireframes(
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds
    ): List<MobileSegment.Wireframe> {
        val rawFillColor = semanticsUtils.resolveCheckboxFillColor(semanticsNode)
        val rawCheckmarkColor = semanticsUtils.resolveCheckmarkColor(semanticsNode)

        val fillColorRgba = rawFillColor?.let {
            convertColor(it)
        } ?: DEFAULT_COLOR_WHITE
        val checkmarkColorRgba = rawCheckmarkColor?.let {
            convertColor(it)
        } ?: getFallbackCheckmarkColor(DEFAULT_COLOR_WHITE)

        val parsedFillColor = pathUtils.parseColorSafe(fillColorRgba)
        val parsedCheckmarkColor = pathUtils.parseColorSafe(checkmarkColorRgba)

        if (parsedFillColor != null && parsedCheckmarkColor != null) {
            val checkMarkBitmap = semanticsUtils
                .resolveCheckPath(semanticsNode)?.let { checkPath ->
                    pathUtils.convertPathToBitmap(
                        checkPath = checkPath,
                        fillColor = parsedFillColor,
                        checkmarkColor = parsedCheckmarkColor
                    )
                }

            if (checkMarkBitmap != null) {
                parentContext.imageWireframeHelper.createImageWireframeByBitmap(
                    id = resolveId(semanticsNode, 0),
                    globalBounds = globalBounds,
                    bitmap = checkMarkBitmap,
                    density = parentContext.density,
                    isContextualImage = false,
                    imagePrivacy = ImagePrivacy.MASK_NONE,
                    asyncJobStatusCallback = asyncJobStatusCallback,
                    clipping = null,
                    shapeStyle = MobileSegment.ShapeStyle(
                        backgroundColor = fillColorRgba,
                        opacity = 1f,
                        cornerRadius = CHECKBOX_CORNER_RADIUS
                    ),
                    border = MobileSegment.ShapeBorder(
                        color = fillColorRgba,
                        width = BOX_BORDER_WIDTH_DP
                    )
                )?.let { imageWireframe ->
                    return listOf(imageWireframe)
                }
            }
        }

        // if we failed to create a wireframe from the path
        return createManualCheckedWireframe(semanticsNode, globalBounds, fillColorRgba)
    }

    private fun createManualCheckedWireframe(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        backgroundColor: String
    ): List<MobileSegment.Wireframe> {
        val strokeColor = getFallbackCheckmarkColor(backgroundColor)

        val background: MobileSegment.Wireframe = createUncheckedWireframes(
            semanticsNode = semanticsNode,
            globalBounds = globalBounds,
            backgroundColor = backgroundColor
        )[0]

        val checkmarkWidth = globalBounds.width * CHECKMARK_SIZE_FACTOR
        val checkmarkHeight = globalBounds.height * CHECKMARK_SIZE_FACTOR
        val xPos = globalBounds.x + ((globalBounds.width / 2) - (checkmarkWidth / 2))
        val yPos = globalBounds.y + ((globalBounds.height / 2) - (checkmarkHeight / 2))
        val foreground: MobileSegment.Wireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = resolveId(semanticsNode, 1),
            x = xPos.toLong(),
            y = yPos.toLong(),
            width = checkmarkWidth.toLong(),
            height = checkmarkHeight.toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = strokeColor,
                opacity = 1f,
                cornerRadius = CHECKBOX_CORNER_RADIUS
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BOX_BORDER_WIDTH_DP
            )
        )
        return listOf(background, foreground)
    }

    private fun createUncheckedWireframes(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        backgroundColor: String
    ): List<MobileSegment.Wireframe> {
        val borderColor =
            semanticsUtils.resolveBorderColor(semanticsNode)
                ?.let { rawColor ->
                    convertColor(rawColor)
                } ?: DEFAULT_COLOR_BLACK

        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                id = resolveId(semanticsNode, 0),
                x = globalBounds.x,
                y = globalBounds.y,
                width = globalBounds.width,
                height = globalBounds.height,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = backgroundColor,
                    opacity = 1f,
                    cornerRadius = CHECKBOX_CORNER_RADIUS
                ),
                border = MobileSegment.ShapeBorder(
                    color = borderColor,
                    width = BOX_BORDER_WIDTH_DP
                )
            )
        )
    }

    private fun isCheckboxChecked(semanticsNode: SemanticsNode): Boolean =
        semanticsNode.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On

    private fun getFallbackCheckmarkColor(backgroundColor: String?) =
        if (backgroundColor == DEFAULT_COLOR_WHITE) {
            DEFAULT_COLOR_BLACK
        } else {
            DEFAULT_COLOR_WHITE
        }

    internal companion object {
        internal const val DEFAULT_COLOR_BLACK = "#000000FF"
        internal const val DEFAULT_COLOR_WHITE = "#FFFFFFFF"

        // when we create the checkmark manually, what % of the checkbox size should it be
        internal const val CHECKMARK_SIZE_FACTOR = 0.5

        // values from Compose Checkbox sourcecode
        internal const val BOX_BORDER_WIDTH_DP = 2L
        internal const val STROKE_WIDTH_DP = 2f
        internal const val CHECKBOX_SIZE_DP = 20
        internal const val CHECKBOX_CORNER_RADIUS = 2f
    }
}
