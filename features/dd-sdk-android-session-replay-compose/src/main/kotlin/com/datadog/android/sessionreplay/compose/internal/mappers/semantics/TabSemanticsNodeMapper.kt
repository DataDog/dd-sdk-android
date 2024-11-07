/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class TabSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val parentFrames = resolveParentColor(semanticsNode)
        return SemanticsWireframe(
            wireframes = parentFrames,
            uiContext = parentContext
        )
    }

    private fun resolveParentColor(semanticsNode: SemanticsNode): List<MobileSegment.Wireframe> {
        val globalBounds = resolveBounds(semanticsNode)

        // TODO RUM-7082: Consider use `UiContext` to pass the color information
        var parentColor = semanticsNode.parent?.let { parent ->
            semanticsUtils.resolveBackgroundColor(parent)?.let {
                convertColor(it)
            }
        }

        // If parent color is not specified, it may be in the grandparent modifier info.
        if (parentColor == null) {
            parentColor = semanticsNode.parent?.parent?.let { grandParentNode ->
                semanticsUtils.resolveBackgroundColor(grandParentNode)?.let {
                    convertColor(it)
                }
            }
        }

        val shapeStyle = MobileSegment.ShapeStyle(backgroundColor = parentColor)
        return MobileSegment.Wireframe.ShapeWireframe(
            id = semanticsNode.id.toLong(),
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            shapeStyle = shapeStyle
        ).let { listOf(it) }
    }
}
