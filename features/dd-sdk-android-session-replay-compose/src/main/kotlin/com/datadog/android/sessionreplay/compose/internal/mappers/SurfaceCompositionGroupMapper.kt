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

/**
 * This class is responsible to map the Jetpack compose components which have "backgroundColor" & "contentColor"
 * in the parameter list, these components are mostly container components such as [BottomNavigation], [TopAppBar]
 * and [TabRow], etc.., they usually have a [Surface] implementation under the hood, and can take other composable
 * inside.
 */
internal open class SurfaceCompositionGroupMapper(colorStringFormatter: ColorStringFormatter) :
    AbstractCompositionGroupMapper(colorStringFormatter) {
    override fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe {
        var backgroundColor: String? = null
        var contentColor: String? = null
        parameters.forEach { param ->
            when (param.name) {
                "backgroundColor" -> (param.value as? Long)?.let {
                    backgroundColor = convertColor(it)
                }

                "contentColor" -> (param.value as? Long)?.let {
                    contentColor = convertColor(it)
                }
            }
        }
        return ComposeWireframe(
            MobileSegment.Wireframe.ShapeWireframe(
                id = stableGroupId,
                x = boxWithDensity.x,
                y = boxWithDensity.y,
                width = boxWithDensity.width,
                height = boxWithDensity.height,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = backgroundColor
                )
            ),
            uiContext.copy(parentContentColor = contentColor)
        )
    }
}
