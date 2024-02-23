/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class TextCompositionGroupMapper : AbstractCompositionGroupMapper() {

    override fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe? {
        var text: String? = null
        var textAlign: MobileSegment.Horizontal? = null
        var fontFamily: String = "Roboto, sans-serif"
        var textColor: String? = uiContext.parentContentColor
        var fontSize: Float? = null

        parameters.forEach { param ->
            when (param.name) {
                "text" -> text = param.value as? String
                "textAlign" -> textAlign = when (param.value as? TextAlign) {
                    TextAlign.Justify,
                    TextAlign.Left,
                    TextAlign.Start -> MobileSegment.Horizontal.LEFT

                    TextAlign.Right,
                    TextAlign.End -> MobileSegment.Horizontal.RIGHT

                    TextAlign.Center -> MobileSegment.Horizontal.CENTER
                    else -> null
                }

                "textColor" -> textColor = (param.value as? Long)?.let { convertColor(it) }
                "fontSize" -> fontSize = (param.value as? Long)?.let { convertTextUnit(it) }
                "fontFamily" -> fontFamily = when (param.value) {
                    is GenericFontFamily -> param.value.name
                    else -> "Roboto, sans-serif"
                }
            }
        }

        return ComposeWireframe(
            MobileSegment.Wireframe.TextWireframe(
                id = stableGroupId,
                x = boxWithDensity.x,
                y = boxWithDensity.y,
                width = boxWithDensity.width,
                height = boxWithDensity.height,
                text = text.orEmpty(),
                textStyle = MobileSegment.TextStyle(
                    family = fontFamily,
                    size = fontSize?.toLong() ?: 12L,
                    color = textColor ?: "#000000FF"
                ),
                textPosition = MobileSegment.TextPosition(
                    alignment = MobileSegment.Alignment(
                        horizontal = textAlign
                    )
                )
            ),
            null
        )
    }

    /**
     * Parse Compose's [androidx.compose.ui.unit.TextUnitType] packed value.
     */
    protected fun convertTextUnit(dimen: Long): Float? {
        return if (dimen == 0x7FC0_0000L) {
            null
        } else {
            val unit = dimen and UNIT_MASK
            val value = Float.fromBits((dimen and VALUE_MASK).toInt())

            when (unit) {
                UNIT_TYPE_UNSPECIFIED,
                UNIT_TYPE_SP -> value
                UNIT_TYPE_EM -> value // TODO find the proper way to scale em to px
                else -> value
            }
        }
    }

    companion object {

        private const val VALUE_MASK = 0xFFFF_FFFFL
        private const val UNIT_MASK = 0xFFL shl 32 // 0xFF_0000_0000
        private const val UNIT_TYPE_UNSPECIFIED = 0x00L shl 32 // 0x00_0000_0000
        private const val UNIT_TYPE_SP = 0x01L shl 32 // 0x01_0000_0000
        private const val UNIT_TYPE_EM = 0x02L shl 32 // 0x2_0000_0000
    }
}
