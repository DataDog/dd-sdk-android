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
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.ColorUtils
import com.datadog.android.sessionreplay.compose.internal.utils.PathUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal class CheckboxSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val colorUtils: ColorUtils = ColorUtils(),
    private val logger: InternalLogger = InternalLogger.UNBOUND,
    private val pathUtils: PathUtils = PathUtils(logger)
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val globalBounds = resolveBounds(semanticsNode)

        val checkableWireframes = if (parentContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
            listOf(
                resolveMaskedCheckable(
                    semanticsNode = semanticsNode,
                    globalBounds = globalBounds
                )
            )
        } else {
            // Resolves checkable view regardless the state
            resolveCheckable(
                semanticsNode = semanticsNode,
                parentContext = parentContext,
                asyncJobStatusCallback = asyncJobStatusCallback,
                globalBounds = globalBounds
            )
        }

        return SemanticsWireframe(
            uiContext = null,
            wireframes = checkableWireframes
        )
    }

    private fun resolveMaskedCheckable(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds
    ): MobileSegment.Wireframe {
        // TODO RUM-5118: Decide how to display masked checkbox, Currently use old unchecked shape wireframe,
        return createUncheckedState(
            semanticsNode = semanticsNode,
            globalBounds = globalBounds,
            backgroundColor = DEFAULT_COLOR_WHITE,
            borderColor = DEFAULT_COLOR_BLACK,
            currentIndex = 0
        )
    }

    private fun resolveCheckable(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        globalBounds: GlobalBounds
    ): List<MobileSegment.Wireframe> {
        return if (isCheckboxChecked(semanticsNode)) {
            createCheckedState(
                parentContext = parentContext,
                asyncJobStatusCallback = asyncJobStatusCallback,
                semanticsNode = semanticsNode,
                globalBounds = globalBounds,
                currentIndex = 0
            )
        } else {
            val borderColor =
                semanticsUtils.resolveBorderColor(semanticsNode)
                    ?.let { rawColor ->
                        convertColor(rawColor)
                    } ?: DEFAULT_COLOR_BLACK

            listOf(
                createUncheckedState(
                    semanticsNode = semanticsNode,
                    globalBounds = globalBounds,
                    backgroundColor = DEFAULT_COLOR_WHITE,
                    borderColor = borderColor,
                    currentIndex = 0
                )
            )
        }
    }

    private fun createCheckedState(
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        currentIndex: Int
    ): List<MobileSegment.Wireframe> {
        val rawFillColor = semanticsUtils.resolveCheckboxFillColor(semanticsNode)
        val rawCheckmarkColor = semanticsUtils.resolveCheckmarkColor(semanticsNode)

        val fillColorRgba = rawFillColor?.let {
            convertColor(it)
        } ?: DEFAULT_COLOR_WHITE
        val checkmarkColorRgba = rawCheckmarkColor?.let {
            convertColor(it)
        } ?: getFallbackCheckmarkColor(DEFAULT_COLOR_WHITE)

        val parsedFillColor = colorUtils.parseColorSafe(fillColorRgba)
        val parsedCheckmarkColor = colorUtils.parseColorSafe(checkmarkColorRgba)
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        if (parsedFillColor != null && parsedCheckmarkColor != null) {
            val androidPath = semanticsUtils
                .resolveCheckPath(semanticsNode)?.let { checkPath ->
                    pathUtils.asAndroidPathSafe(checkPath)
                }

            if (androidPath != null) {
                parentContext.imageWireframeHelper.createImageWireframeByPath(
                    id = resolveId(semanticsNode, currentIndex),
                    globalBounds = globalBounds,
                    path = androidPath,
                    strokeColor = parsedCheckmarkColor,
                    strokeWidth = STROKE_WIDTH_DP.toInt(),
                    targetWidth = CHECKBOX_SIZE_DP,
                    targetHeight = CHECKBOX_SIZE_DP,
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
                    ),
                    customResourceIdCacheKey = null
                )?.let { imageWireframe ->
                    wireframes.add(imageWireframe)
                }
            }
        }

        if (wireframes.isNotEmpty()) {
            return wireframes
        }

        // if we failed to create a wireframe from the path
        return createManualCheckedWireframe(
            semanticsNode = semanticsNode,
            globalBounds = globalBounds,
            backgroundColor = fillColorRgba
        )
    }

    private fun createManualCheckedWireframe(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        backgroundColor: String
    ): List<MobileSegment.Wireframe> {
        val strokeColor = getFallbackCheckmarkColor(backgroundColor)

        val wireframesList = mutableListOf<MobileSegment.Wireframe>()

        val borderColor =
            semanticsUtils.resolveBorderColor(semanticsNode)
                ?.let { rawColor ->
                    convertColor(rawColor)
                } ?: DEFAULT_COLOR_BLACK

        val background = createUncheckedState(
            semanticsNode = semanticsNode,
            globalBounds = globalBounds,
            backgroundColor = backgroundColor,
            borderColor = borderColor,
            currentIndex = 0
        )

        wireframesList.add(background)

        val checkmarkWidth = globalBounds.width * CHECKMARK_SIZE_FACTOR
        val checkmarkHeight = globalBounds.height * CHECKMARK_SIZE_FACTOR
        val xPos = globalBounds.x + ((globalBounds.width / 2) - (checkmarkWidth / 2))
        val yPos = globalBounds.y + ((globalBounds.height / 2) - (checkmarkHeight / 2))
        val foreground = MobileSegment.Wireframe.ShapeWireframe(
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

        wireframesList.add(foreground)
        return wireframesList
    }

    private fun createUncheckedState(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        backgroundColor: String,
        borderColor: String,
        currentIndex: Int
    ): MobileSegment.Wireframe {
        return MobileSegment.Wireframe.ShapeWireframe(
            id = resolveId(semanticsNode, currentIndex),
            x = globalBounds.x,
            y = globalBounds.y,
            width = CHECKBOX_SIZE_DP.toLong(),
            height = CHECKBOX_SIZE_DP.toLong(),
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
