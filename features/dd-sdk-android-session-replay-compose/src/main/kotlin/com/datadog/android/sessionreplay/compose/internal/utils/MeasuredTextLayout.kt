/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.model.MobileSegment
import kotlin.math.ceil

/** Copied metadata for a fitting plain paragraph; never retained beyond the existing metadata cache. */
internal class MeasuredTextLayout(val text: String, val style: TextStyle, val isMultiline: Boolean) {
    fun toTextLayoutInfo(
        modifierColor: Color?,
        textOverflow: MobileSegment.TruncationMode?
    ): TextLayoutInfo = TextLayoutInfo(
        text = text,
        sourceText = text,
        color = modifierColor?.value ?: style.color.value,
        textAlign = style.textAlign,
        fontSize = style.fontSize.value.let { if (it.isNaN()) SemanticsUtils.DEFAULT_FONT_SIZE_SP else it.toLong() },
        fontFamily = style.fontFamily,
        textOverflow = textOverflow,
        requiresPixelCapture = isMultiline || requiresTextPixels(style)
    )

    companion object {
        // The wire format cannot represent these styles. Keep the measured glyphs instead of
        // substituting a font or asking the player to reflow differently styled text.
        fun requiresTextPixels(style: TextStyle): Boolean {
            return hasUnsupportedFontStyle(style) || hasCustomTextSpacing(style) ||
                style.brush != null || (style.shadow != null && style.shadow != Shadow.None) ||
                (style.textDecoration != null && style.textDecoration != TextDecoration.None)
        }

        private fun hasUnsupportedFontStyle(style: TextStyle): Boolean =
            (style.fontWeight != null && style.fontWeight != FontWeight.Normal) ||
                (style.fontStyle != null && style.fontStyle != FontStyle.Normal)

        private fun hasCustomTextSpacing(style: TextStyle): Boolean =
            ((style.letterSpacing.isSp || style.letterSpacing.isEm) && style.letterSpacing.value != 0f) ||
                style.lineHeight.isSp || style.lineHeight.isEm

        @Suppress("UnsafeThirdPartyFunctionCall") // Primitive finite-value check.
        private fun fitsDimension(measured: Float, size: Int, maximum: Int): Boolean =
            measured.isFinite() && measured > 0f && size > 0 && measured <= size && measured <= maximum

        /**
         * SimpleString semantics rebuild at the parent's maximum width. The measured paragraph
         * must either use that same width, or already fit every intrinsic line at a narrower width.
         * Overflow and no-wrap layouts keep the action because its synthetic line count/text may differ.
         */
        @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Read current UI-thread layout metadata only.
        fun canReuse(
            paragraph: Paragraph,
            intrinsics: ParagraphIntrinsics,
            constraints: Constraints,
            size: IntSize,
            softWrap: Boolean
        ): Boolean {
            if (!softWrap || intrinsics.hasStaleResolvedFonts || paragraph.didExceedMaxLines) return false
            val bounds = constraints
            val width = paragraph.width
            val height = paragraph.height
            if (!bounds.hasBoundedWidth || paragraph.lineCount <= 0) return false
            if (!fitsDimension(width, size.width, bounds.maxWidth) ||
                !fitsDimension(height, size.height, bounds.maxHeight)
            ) {
                return false
            }
            val intrinsicWidth = intrinsics.maxIntrinsicWidth
            return width == bounds.maxWidth.toFloat() ||
                (intrinsicWidth.isFinite() && intrinsicWidth >= 0f && ceil(intrinsicWidth) <= width)
        }
    }
}
