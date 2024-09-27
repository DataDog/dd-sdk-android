/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal class ButtonSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter) {

    override fun map(semanticsNode: SemanticsNode, parentContext: UiContext): ComposeWireframe {
        val density = semanticsNode.layoutInfo.density
        val bounds = resolveBound(semanticsNode)
        val buttonStyle = resolveSemanticsButtonStyle(semanticsNode, bounds, density)
        return ComposeWireframe(
            MobileSegment.Wireframe.ShapeWireframe(
                id = semanticsNode.id.toLong(),
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                shapeStyle = buttonStyle
            ),
            uiContext = parentContext.copy(
                parentContentColor = buttonStyle.backgroundColor ?: parentContext.parentContentColor
            )
        )
    }

    private fun resolveSemanticsButtonStyle(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        density: Density
    ): MobileSegment.ShapeStyle {
        val color = semanticsUtils.resolveSemanticsModifierColor(semanticsNode)
        val cornerRadius = semanticsUtils.resolveSemanticsModifierCornerRadius(semanticsNode, globalBounds, density)
        return MobileSegment.ShapeStyle(
            backgroundColor = color?.let { convertColor(it) },
            cornerRadius = cornerRadius
        )
    }
}
