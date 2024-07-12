/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonColors
import androidx.compose.ui.geometry.Size
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.reflection.accessible
import com.datadog.android.sessionreplay.compose.internal.reflection.getDeclaredFieldSafe
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import java.lang.reflect.Field

internal class ButtonCompositionGroupMapper(
    colorStringFormatter: ColorStringFormatter
) : AbstractCompositionGroupMapper(colorStringFormatter) {

    override fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe {
        var cornerRadius: Number? = null
        var colors: ButtonColors? = null

        parameters.forEach { param ->
            when (param.name) {
                "colors" -> colors = param.value as? ButtonColors
                "shape" -> cornerRadius = (param.value as? RoundedCornerShape)?.let {
                    // We only have a single value for corner radius, so we default to using the
                    // top left (i.e.: topStart) corner's value and apply it to all corners
                    it.topStart.toPx(Size.Unspecified, uiContext.composeDensity) / uiContext.density
                }
            }
        }

        var backgroundColor: String? = null
        var contentColor: String? = uiContext.parentContentColor

        colors?.let { c ->
            backgroundColor = getBackgroundColor(c)?.let { convertColor(it) }
            contentColor = (getContentColor(c))?.let { convertColor(it) }
        }

        return ComposeWireframe(
            MobileSegment.Wireframe.ShapeWireframe(
                id = stableGroupId,
                x = boxWithDensity.x,
                y = boxWithDensity.y,
                width = boxWithDensity.width,
                height = boxWithDensity.height,
                shapeStyle = MobileSegment.ShapeStyle(
                    cornerRadius = cornerRadius,
                    backgroundColor = backgroundColor
                ),
                border = MobileSegment.ShapeBorder(
                    color = "#000000",
                    width = 1
                )
            ),
            uiContext.copy(parentContentColor = contentColor)
        )
    }

    companion object {
        private val backgroundColorFields = mutableMapOf<Class<*>, Field?>()
        private val contentColorFields = mutableMapOf<Class<*>, Field?>()

        fun getBackgroundColor(buttonColors: ButtonColors): Long? {
            val javaClass = buttonColors.javaClass
            val field = backgroundColorFields.getOrElse(javaClass) {
                javaClass.getDeclaredFieldSafe("backgroundColor")?.accessible()
            }
            backgroundColorFields[javaClass] = field

            return field?.getSafe(buttonColors) as? Long
        }

        fun getContentColor(buttonColors: ButtonColors): Long? {
            val javaClass = buttonColors.javaClass
            val field = contentColorFields.getOrElse(javaClass) {
                javaClass.getDeclaredFieldSafe("contentColor")?.accessible()
            }
            contentColorFields[javaClass] = field

            return field?.getSafe(buttonColors) as? Long
        }
    }
}
