/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class TextCompositionGroupMapper(
    colorStringFormatter: ColorStringFormatter
) : AbstractCompositionGroupMapper(colorStringFormatter) {

    override fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe {
        val text: String = resolveCapturedText(
            parameters = parameters,
            uiContext = uiContext
        )
        var textAlign: MobileSegment.Horizontal? = null
        var fontFamily = DEFAULT_FONT_FAMILY
        var textColor: String? = uiContext.parentContentColor
        var fontSize: Float? = null

        parameters.forEach { param ->
            when (param.name) {
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
                    else -> DEFAULT_FONT_FAMILY
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
                text = text,
                textStyle = MobileSegment.TextStyle(
                    family = fontFamily,
                    size = fontSize?.toLong() ?: DEFAULT_FONT_SIZE,
                    color = textColor ?: DEFAULT_TEXT_COLOR
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

    private fun resolveCapturedText(parameters: Sequence<ComposableParameter>, uiContext: UiContext): String {
        val originalText = (parameters.firstOrNull { it.name == PARAM_NAME_TEXT }?.value as? String).orEmpty()
        return when (uiContext.privacy) {
            SessionReplayPrivacy.ALLOW -> originalText
            SessionReplayPrivacy.MASK_USER_INPUT ->
                if (uiContext.isInUserInputLayout) FIXED_INPUT_MASK else originalText

            SessionReplayPrivacy.MASK ->
                if (uiContext.isInUserInputLayout) {
                    FIXED_INPUT_MASK
                } else {
                    StringObfuscator.getStringObfuscator().obfuscate(originalText)
                }
        }
    }

    /**
     * Parse Compose's [androidx.compose.ui.unit.TextUnitType] packed value.
     */
    private fun convertTextUnit(dimen: Long): Float? {
        val dimenType = dimen and DIMEN_TYPE_MASK
        val dimenValue = dimen and DIMEN_VALUE_MASK

        return if (dimenValue == DIMEN_VALUE_NAN) {
            null
        } else {
            val value = Float.fromBits(dimenValue.toInt())

            when (dimenType) {
                DIMEN_TYPE_UNSPECIFIED,
                DIMEN_TYPE_SP -> value // RUM-3633 scale by density ?

                DIMEN_TYPE_EM -> value // TODO RUM-3633 find the proper way to scale em to px
                else -> value
            }
        }
    }

    companion object {
        internal const val FIXED_INPUT_MASK = "***"
        internal const val PARAM_NAME_TEXT = "text"

        internal const val DEFAULT_FONT_FAMILY = "Roboto, sans-serif"
        internal const val DEFAULT_TEXT_COLOR = "#000000FF"
        internal const val DEFAULT_FONT_SIZE = 12L

        private const val DIMEN_TYPE_MASK = 0xFFL shl 32 // 0xFF_0000_0000
        private const val DIMEN_VALUE_MASK = 0xFFFF_FFFFL

        private const val DIMEN_VALUE_NAN = 0x7FC0_0000L

        private const val DIMEN_TYPE_UNSPECIFIED = 0x00L shl 32 // 0x00_0000_0000
        private const val DIMEN_TYPE_SP = 0x01L shl 32 // 0x01_0000_0000
        private const val DIMEN_TYPE_EM = 0x02L shl 32 // 0x02_0000_0000
    }
}
