/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.ui.text.TextStyle
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

        // Resolve text style first, because it can be overridden if the color and font size are specified directly.
        (parameters.firstOrNull { it.name == PARAM_NAME_STYLE }?.value as? TextStyle)?.let {
            fontSize = it.fontSize.value
            textColor = convertColor(it.color.value.toLong()) ?: textColor
            textAlign = resolveTextAlign(it.textAlign)
        }

        parameters.forEach { param ->
            when (param.name) {
                PARAM_NAME_TEXT_ALIGN -> resolveTextAlign(param.value as? TextAlign)?.let {
                    textAlign = it
                }

                PARAM_NAME_COLOR -> (param.value as? Long)?.let { convertColor(it) }?.let {
                    textColor = it
                }

                PARAM_NAME_FONT_SIZE -> (param.value as? Long)?.let { convertTextUnit(it) }?.let {
                    fontSize = it
                }

                PARAM_NAME_FONT_FAMILY -> fontFamily = when (param.value) {
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

    private fun resolveTextAlign(textAlign: TextAlign?): MobileSegment.Horizontal? {
        return when (textAlign) {
            TextAlign.Justify,
            TextAlign.Left,
            TextAlign.Start -> MobileSegment.Horizontal.LEFT

            TextAlign.Right,
            TextAlign.End -> MobileSegment.Horizontal.RIGHT

            TextAlign.Center -> MobileSegment.Horizontal.CENTER
            else -> null
        }
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
        // TODO: RUM-6058 Support system font scale in Text mapper
        val fontScale = 1f
        return if (dimenValue == DIMEN_VALUE_NAN) {
            null
        } else {
            val value = Float.fromBits(dimenValue.toInt())

            when (dimenType) {
                DIMEN_TYPE_UNSPECIFIED,
                // "em" is relative font size, it should not be applied on font size it self, it should be used in letterSpacing, lineHeight, etc.
                DIMEN_TYPE_EM -> value

                DIMEN_TYPE_SP -> value * fontScale
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

        private const val PARAM_NAME_STYLE = "style"
        private const val PARAM_NAME_TEXT_ALIGN = "textAlign"
        private const val PARAM_NAME_FONT_SIZE = "fontSize"
        private const val PARAM_NAME_FONT_FAMILY = "fontFamily"
        private const val PARAM_NAME_COLOR = "color"
    }
}
