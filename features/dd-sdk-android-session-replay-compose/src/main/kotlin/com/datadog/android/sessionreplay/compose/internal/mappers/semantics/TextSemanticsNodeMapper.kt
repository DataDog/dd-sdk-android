/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class TextSemanticsNodeMapper(colorStringFormatter: ColorStringFormatter) :
    AbstractSemanticsNodeMapper(colorStringFormatter) {
    override fun map(semanticsNode: SemanticsNode, parentContext: UiContext): ComposeWireframe {
        val text = resolveText(semanticsNode.config)
        val textStyle = resolveTextStyle(semanticsNode, parentContext) ?: defaultTextStyle
        val bounds = resolveBound(semanticsNode)
        return ComposeWireframe(
            MobileSegment.Wireframe.TextWireframe(
                id = semanticsNode.id.toLong(),
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                text = text ?: "",
                textStyle = textStyle,
                textPosition = resolveTextAlign(semanticsNode)
            ),
            null
        )
    }

    private fun resolveTextAlign(semanticsNode: SemanticsNode): MobileSegment.TextPosition? {
        return resolveSemanticsTextStyle(semanticsNode)?.let {
            val align = when (it.textAlign) {
                TextAlign.Start,
                TextAlign.Left -> MobileSegment.Horizontal.LEFT

                TextAlign.End,
                TextAlign.Right -> MobileSegment.Horizontal.RIGHT

                TextAlign.Justify,
                TextAlign.Center -> MobileSegment.Horizontal.CENTER

                else -> MobileSegment.Horizontal.LEFT
            }
            MobileSegment.TextPosition(
                alignment = MobileSegment.Alignment(
                    horizontal = align
                )
            )
        }
    }

    private fun resolveTextStyle(semanticsNode: SemanticsNode, parentContext: UiContext): MobileSegment.TextStyle? {
        return resolveSemanticsTextStyle(semanticsNode)?.let { textStyle ->
            val color = resolveModifierColor(semanticsNode) ?: textStyle.color
            MobileSegment.TextStyle(
                family = when (val value = textStyle.fontFamily) {
                    is GenericFontFamily -> value.name
                    else -> DEFAULT_FONT_FAMILY
                },
                size = textStyle.fontSize.value.toLong(),
                color = convertColor(color.value.toLong()) ?: parentContext.parentContentColor
                    ?: DEFAULT_TEXT_COLOR
            )
        }
    }

    private fun resolveModifierColor(semanticsNode: SemanticsNode): Color? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            ComposeReflection.TextStringSimpleElement?.isInstance(it.modifier) ?: false
        }?.modifier
        return if (modifier != null && ComposeReflection.TextStringSimpleElement?.isInstance(modifier) == true) {
            val colorProducer = ComposeReflection.ColorProducerField?.getSafe(modifier) as? ColorProducer
            return colorProducer?.invoke()
        } else {
            null
        }
    }

    private fun resolveSemanticsTextStyle(semanticsNode: SemanticsNode): TextStyle? {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        semanticsNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(textLayoutResults)
        return textLayoutResults.firstOrNull()?.layoutInput?.style
    }

    private fun resolveText(semanticsConfiguration: SemanticsConfiguration): String? {
        return semanticsConfiguration.firstOrNull {
            it.key.name == KEY_CONFIG_TEXT
        }?.let {
            return resolveAnnotatedString(it.value)
        }
    }

    private fun resolveAnnotatedString(value: Any?): String {
        return if (value is AnnotatedString) {
            if (value.paragraphStyles.isEmpty() &&
                value.spanStyles.isEmpty() &&
                value.getStringAnnotations(0, value.text.length).isEmpty()
            ) {
                value.text
            } else {
                // Save space if we there is text only in the object
                value.toString()
            }
        } else if (value is Collection<*>) {
            val sb = StringBuilder()
            value.forEach {
                resolveAnnotatedString(it).let {
                    sb.append(it)
                }
            }
            sb.toString()
        } else {
            value.toString()
        }
    }

    companion object {
        private const val KEY_CONFIG_TEXT = "Text"
        private const val DEFAULT_FONT_FAMILY = "Roboto, sans-serif"
        private const val DEFAULT_TEXT_COLOR = "#000000FF"
        private const val DEFAULT_FONT_SIZE = 12L
        private val defaultTextStyle = MobileSegment.TextStyle(
            size = DEFAULT_FONT_SIZE,
            color = DEFAULT_TEXT_COLOR,
            family = DEFAULT_FONT_FAMILY
        )
    }
}
