/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import androidx.core.text.TextDirectionHeuristicsCompat
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.internal.composition.CapturedAlignment
import com.datadog.android.sessionreplay.internal.composition.CapturedHorizontalAlignment
import com.datadog.android.sessionreplay.internal.composition.CapturedPadding
import com.datadog.android.sessionreplay.internal.composition.CapturedTextPosition
import com.datadog.android.sessionreplay.internal.composition.CapturedTextStyle
import com.datadog.android.sessionreplay.internal.composition.CapturedTruncationMode
import com.datadog.android.sessionreplay.internal.composition.CapturedVerticalAlignment
import com.datadog.android.sessionreplay.internal.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * The flagship semantic wireframe for this workstream: a real, unmasked text capture. Text/input
 * privacy masking is explicitly out of scope here - it is owned by the pixel-fallback/privacy
 * workstream, which applies masking policy uniformly across text and images before anything is
 * uploadable. Styling follows legacy `TextViewMapper`; relative alignment is resolved using the
 * rendered paragraph direction for text alignment and layout direction for view alignment.
 */
internal class CapturedTextViewMapper(
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver =
        CapturedBackgroundShapeStyleResolver(),
    private val internalLogger: InternalLogger
) : CapturedViewMapper<TextView> {

    override fun map(view: TextView, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val wireframes = mutableListOf<CapturedWireframe>()
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val capturedBounds = bounds.toCaptured()

        backgroundShapeStyleResolver.resolve(view, internalLogger)?.let { style ->
            wireframes += CapturedWireframe.Shape(
                identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                bounds = capturedBounds,
                style = style
            )
        }

        wireframes += CapturedWireframe.Text(
            identity = mappingContext.identityFactory.textWireframe(mappingContext.ownerIdentity),
            bounds = capturedBounds,
            text = resolveLayoutText(view),
            textStyle = resolveTextStyle(view, mappingContext.screenDensity),
            textPosition = resolveTextPosition(view, mappingContext.screenDensity)
        )

        return CapturedViewMapperResult.Wireframes(wireframes)
    }

    private fun resolveTextStyle(textView: TextView, pixelsDensity: Float): CapturedTextStyle {
        return CapturedTextStyle(
            family = resolveFontFamily(textView.typeface),
            size = textView.textSize.toLong().densityNormalized(pixelsDensity),
            color = resolveTextColor(textView),
            truncationMode = resolveTruncationMode(textView)
        )
    }

    private fun resolveTextColor(textView: TextView): String {
        return if (textView.text.isNullOrEmpty()) {
            resolveHintTextColor(textView)
        } else {
            colorStringFormatter.formatColorAndAlphaAsHexString(textView.currentTextColor, OPAQUE_ALPHA_VALUE)
        }
    }

    private fun resolveHintTextColor(textView: TextView): String {
        val hintTextColors = textView.hintTextColors
        return if (hintTextColors != null) {
            colorStringFormatter.formatColorAndAlphaAsHexString(hintTextColors.defaultColor, OPAQUE_ALPHA_VALUE)
        } else {
            colorStringFormatter.formatColorAndAlphaAsHexString(textView.currentTextColor, OPAQUE_ALPHA_VALUE)
        }
    }

    private fun resolveFontFamily(typeface: Typeface?): String {
        return when (typeface) {
            Typeface.SANS_SERIF -> SANS_SERIF_FAMILY_NAME
            Typeface.MONOSPACE -> MONOSPACE_FAMILY_NAME
            Typeface.SERIF -> SERIF_FAMILY_NAME
            else -> SANS_SERIF_FAMILY_NAME
        }
    }

    private fun resolveTruncationMode(textView: TextView): CapturedTruncationMode? {
        return textView.ellipsize?.let { truncationMode ->
            when (truncationMode) {
                TextUtils.TruncateAt.START -> CapturedTruncationMode.HEAD
                TextUtils.TruncateAt.END -> CapturedTruncationMode.TAIL
                TextUtils.TruncateAt.MIDDLE -> CapturedTruncationMode.MIDDLE
                TextUtils.TruncateAt.MARQUEE -> CapturedTruncationMode.CLIP
            }
        }
    }

    private fun resolveTextPosition(textView: TextView, pixelsDensity: Float): CapturedTextPosition {
        return CapturedTextPosition(
            padding = resolvePadding(textView, pixelsDensity),
            alignment = resolveAlignment(textView)
        )
    }

    private fun resolvePadding(textView: TextView, pixelsDensity: Float): CapturedPadding {
        return if (textView.layout != null) {
            CapturedPadding(
                top = textView.totalPaddingTop.densityNormalized(pixelsDensity).toLong(),
                bottom = textView.totalPaddingBottom.densityNormalized(pixelsDensity).toLong(),
                left = textView.totalPaddingLeft.densityNormalized(pixelsDensity).toLong(),
                right = textView.totalPaddingRight.densityNormalized(pixelsDensity).toLong()
            )
        } else {
            CapturedPadding(
                top = textView.paddingTop.densityNormalized(pixelsDensity).toLong(),
                bottom = textView.paddingBottom.densityNormalized(pixelsDensity).toLong(),
                left = textView.paddingLeft.densityNormalized(pixelsDensity).toLong(),
                right = textView.paddingRight.densityNormalized(pixelsDensity).toLong()
            )
        }
    }

    private fun resolveAlignment(textView: TextView): CapturedAlignment {
        val horizontal = when (textView.textAlignment) {
            TextView.TEXT_ALIGNMENT_CENTER -> CapturedHorizontalAlignment.CENTER
            TextView.TEXT_ALIGNMENT_TEXT_START -> relativeAlignment(isParagraphRtl(textView), isEnd = false)
            TextView.TEXT_ALIGNMENT_TEXT_END -> relativeAlignment(isParagraphRtl(textView), isEnd = true)
            TextView.TEXT_ALIGNMENT_VIEW_START -> relativeAlignment(
                textView.layoutDirection == TextView.LAYOUT_DIRECTION_RTL,
                isEnd = false
            )
            TextView.TEXT_ALIGNMENT_VIEW_END -> relativeAlignment(
                textView.layoutDirection == TextView.LAYOUT_DIRECTION_RTL,
                isEnd = true
            )
            TextView.TEXT_ALIGNMENT_GRAVITY -> return resolveAlignmentFromGravity(textView)
            else -> CapturedHorizontalAlignment.LEFT
        }
        // Total padding already carries gravity offsets once the text has a layout.
        val vertical = if (textView.layout == null) {
            resolveVerticalAlignment(textView.gravity)
        } else {
            CapturedVerticalAlignment.CENTER
        }
        return CapturedAlignment(horizontal, vertical)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // First line exists; heuristic range covers a captured string.
    private fun isParagraphRtl(textView: TextView): Boolean {
        val layout = textView.layout
        if (layout != null && layout.lineCount > 0) return layout.getParagraphDirection(0) < 0

        val heuristic = when (textView.textDirection) {
            TextView.TEXT_DIRECTION_LTR -> TextDirectionHeuristicsCompat.LTR
            TextView.TEXT_DIRECTION_RTL -> TextDirectionHeuristicsCompat.RTL
            TextView.TEXT_DIRECTION_ANY_RTL -> TextDirectionHeuristicsCompat.ANYRTL_LTR
            TextView.TEXT_DIRECTION_LOCALE -> TextDirectionHeuristicsCompat.LOCALE
            TextView.TEXT_DIRECTION_FIRST_STRONG_LTR -> TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR
            TextView.TEXT_DIRECTION_FIRST_STRONG_RTL -> TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL
            else -> if (textView.layoutDirection == TextView.LAYOUT_DIRECTION_RTL) {
                TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL
            } else {
                TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR
            }
        }
        val text = resolveLayoutText(textView)
        return heuristic.isRtl(text, 0, text.length)
    }

    private fun resolveAlignmentFromGravity(textView: TextView): CapturedAlignment {
        val horizontalAlignment = when (textView.gravity.and(Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            Gravity.START -> relativeAlignment(isParagraphRtl(textView), isEnd = false)
            Gravity.END -> relativeAlignment(isParagraphRtl(textView), isEnd = true)
            Gravity.LEFT -> CapturedHorizontalAlignment.LEFT
            Gravity.RIGHT -> CapturedHorizontalAlignment.RIGHT

            Gravity.CENTER,
            Gravity.CENTER_HORIZONTAL -> CapturedHorizontalAlignment.CENTER

            else -> CapturedHorizontalAlignment.LEFT
        }
        return CapturedAlignment(horizontalAlignment, resolveVerticalAlignment(textView.gravity))
    }

    private companion object {
        const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
        const val SERIF_FAMILY_NAME = "serif"
        const val MONOSPACE_FAMILY_NAME = "monospace"
    }
}

private fun relativeAlignment(isRtl: Boolean, isEnd: Boolean): CapturedHorizontalAlignment =
    if (isRtl != isEnd) CapturedHorizontalAlignment.RIGHT else CapturedHorizontalAlignment.LEFT

private fun resolveVerticalAlignment(gravity: Int): CapturedVerticalAlignment =
    when (gravity.and(Gravity.VERTICAL_GRAVITY_MASK)) {
        Gravity.TOP -> CapturedVerticalAlignment.TOP
        Gravity.BOTTOM -> CapturedVerticalAlignment.BOTTOM
        else -> CapturedVerticalAlignment.CENTER
    }

private fun resolveLayoutText(textView: TextView): String =
    (textView.layout?.text ?: textView.text)?.toString().orEmpty()
