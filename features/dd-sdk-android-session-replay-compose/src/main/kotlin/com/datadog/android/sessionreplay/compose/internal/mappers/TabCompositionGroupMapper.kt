/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class TabCompositionGroupMapper(
    colorStringFormatter: ColorStringFormatter
) : AbstractCompositionGroupMapper(colorStringFormatter) {
    override fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe {
        val isSelected =
            parameters.firstOrNull { it.name == "selected" }?.value as? Boolean ?: false
        val textColor = if (isSelected) {
            parseSelectedContentColor(parameters)
        } else {
            parseUnselectedContentColor(parameters)
        } ?: uiContext.parentContentColor
        return ComposeWireframe(
            MobileSegment.Wireframe.ShapeWireframe(
                id = stableGroupId,
                x = boxWithDensity.x,
                y = boxWithDensity.y,
                width = boxWithDensity.width,
                height = boxWithDensity.height
            ),
            uiContext.copy(parentContentColor = textColor)
        )
    }

    private fun parseSelectedContentColor(params: Sequence<ComposableParameter>): String? {
        return (params.firstOrNull { it.name == "selectedContentColor" }?.value as? Long)?.let {
            convertColor(it)
        }
    }

    private fun parseUnselectedContentColor(params: Sequence<ComposableParameter>): String? {
        return (params.firstOrNull { it.name == "unselectedContentColor" }?.value as? Long)?.let {
            convertColor(it)
        }
    }
}
