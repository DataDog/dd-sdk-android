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
import com.datadog.android.sessionreplay.internal.composition.CapturedBounds
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
    private val backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver,
    private val internalLogger: InternalLogger
) : CapturedViewMapper<TextView> {

    // The mapper belongs to one recording session and is accessed by UI-thread traversal.
    private var hasReportedUnsupportedFont = false

    override fun map(view: TextView, mappingContext: CapturedMappingContext): CapturedViewMapperResult =
        capture(view, mappingContext).map(mappingContext)

    override fun capture(view: TextView, mappingContext: CapturedMappingContext): CapturedViewData {
        val layout = view.layout
        return CapturedTextViewData(
            bounds = (
                mappingContext.bounds
                    ?: viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity).toCaptured()
                ),
            text = (layout?.text ?: view.text)?.toString().orEmpty(),
            background = backgroundShapeStyleResolver.capture(view),
            family = resolveFontFamily(view.typeface),
            textSizePx = view.textSize.toLong(),
            textColor = if (view.text.isNullOrEmpty()) {
                if (view.hintTextColors != null) view.currentHintTextColor else view.currentTextColor
            } else {
                view.currentTextColor
            },
            truncationMode = when (view.ellipsize) {
                TextUtils.TruncateAt.START -> CapturedTruncationMode.HEAD
                TextUtils.TruncateAt.END -> CapturedTruncationMode.TAIL
                TextUtils.TruncateAt.MIDDLE -> CapturedTruncationMode.MIDDLE
                TextUtils.TruncateAt.MARQUEE -> CapturedTruncationMode.CLIP
                null -> null
            },
            paddingTopPx = if (layout != null) view.totalPaddingTop else view.paddingTop,
            paddingBottomPx = if (layout != null) view.totalPaddingBottom else view.paddingBottom,
            paddingLeftPx = if (layout != null) view.totalPaddingLeft else view.paddingLeft,
            paddingRightPx = if (layout != null) view.totalPaddingRight else view.paddingRight,
            textAlignment = view.textAlignment,
            gravity = view.gravity,
            isParagraphRtl = isParagraphRtl(view),
            isLayoutRtl = view.layoutDirection == TextView.LAYOUT_DIRECTION_RTL,
            hasLayout = layout != null,
            colorStringFormatter = colorStringFormatter
        )
    }

    private fun resolveFontFamily(typeface: Typeface?): String {
        return when (typeface) {
            Typeface.SANS_SERIF -> SANS_SERIF_FAMILY_NAME
            Typeface.MONOSPACE -> MONOSPACE_FAMILY_NAME
            Typeface.SERIF -> SERIF_FAMILY_NAME
            null, Typeface.DEFAULT, Typeface.DEFAULT_BOLD -> SANS_SERIF_FAMILY_NAME
            else -> {
                @Suppress("UnsafeThirdPartyFunctionCall") // Typeface.style is a valid defaultFromStyle index.
                val shouldReportFallback = !hasReportedUnsupportedFont &&
                    typeface != Typeface.defaultFromStyle(typeface.style)
                if (shouldReportFallback) {
                    // Guard before logging so later frames create no messages or telemetry properties.
                    hasReportedUnsupportedFont = true
                    internalLogger.log(
                        level = InternalLogger.Level.INFO,
                        target = InternalLogger.Target.TELEMETRY,
                        messageBuilder = { "Unable to resolve text font family; using sans-serif fallback" },
                        onlyOnce = true
                    )
                }
                SANS_SERIF_FAMILY_NAME
            }
        }
    }

    private fun isParagraphRtl(textView: TextView): Boolean {
        val layout = textView.layout
        if (layout != null && layout.lineCount > 0) {
            @Suppress("UnsafeThirdPartyFunctionCall") // The first line exists.
            return layout.getParagraphDirection(0) < 0
        }

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
        @Suppress("UnsafeThirdPartyFunctionCall") // Range covers the captured string.
        return heuristic.isRtl(text, 0, text.length)
    }

    private companion object {
        const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
        const val SERIF_FAMILY_NAME = "serif"
        const val MONOSPACE_FAMILY_NAME = "monospace"
    }
}

private data class CapturedTextViewData(
    val bounds: CapturedBounds,
    val text: String,
    val background: CapturedBackgroundColor?,
    val family: String,
    val textSizePx: Long,
    val textColor: Int,
    val truncationMode: CapturedTruncationMode?,
    val paddingTopPx: Int,
    val paddingBottomPx: Int,
    val paddingLeftPx: Int,
    val paddingRightPx: Int,
    val textAlignment: Int,
    val gravity: Int,
    val isParagraphRtl: Boolean,
    val isLayoutRtl: Boolean,
    val hasLayout: Boolean,
    val colorStringFormatter: ColorStringFormatter
) : CapturedViewData {
    override val isOcclusionBounded: Boolean get() = true
    override val hasOpaqueBackground: Boolean get() = background?.isOpaque == true

    override fun map(mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val wireframes = mutableListOf<CapturedWireframe>()
        background?.let {
            wireframes += CapturedWireframe.Shape(
                identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                bounds = bounds,
                style = it.resolve()
            )
        }
        val density = mappingContext.screenDensity
        wireframes += CapturedWireframe.Text(
            identity = mappingContext.identityFactory.textWireframe(mappingContext.ownerIdentity),
            bounds = bounds,
            text = text,
            textStyle = CapturedTextStyle(
                family = family,
                size = textSizePx.densityNormalized(density),
                color = colorStringFormatter.formatColorAsHexString(textColor),
                truncationMode = truncationMode
            ),
            textPosition = CapturedTextPosition(
                padding = CapturedPadding(
                    top = paddingTopPx.densityNormalized(density).toLong(),
                    bottom = paddingBottomPx.densityNormalized(density).toLong(),
                    left = paddingLeftPx.densityNormalized(density).toLong(),
                    right = paddingRightPx.densityNormalized(density).toLong()
                ),
                alignment = resolveAlignment()
            )
        )
        return CapturedViewMapperResult.Wireframes(wireframes)
    }

    private fun resolveAlignment(): CapturedAlignment {
        val horizontal = when (textAlignment) {
            TextView.TEXT_ALIGNMENT_CENTER -> CapturedHorizontalAlignment.CENTER
            TextView.TEXT_ALIGNMENT_TEXT_START -> relativeAlignment(isParagraphRtl, isEnd = false)
            TextView.TEXT_ALIGNMENT_TEXT_END -> relativeAlignment(isParagraphRtl, isEnd = true)
            TextView.TEXT_ALIGNMENT_VIEW_START -> relativeAlignment(isLayoutRtl, isEnd = false)
            TextView.TEXT_ALIGNMENT_VIEW_END -> relativeAlignment(isLayoutRtl, isEnd = true)
            TextView.TEXT_ALIGNMENT_GRAVITY -> return resolveAlignmentFromGravity()
            else -> CapturedHorizontalAlignment.LEFT
        }
        val vertical = if (hasLayout) CapturedVerticalAlignment.CENTER else resolveVerticalAlignment(gravity)
        return CapturedAlignment(horizontal, vertical)
    }

    private fun resolveAlignmentFromGravity(): CapturedAlignment {
        val horizontal = when (gravity.and(Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            Gravity.START -> relativeAlignment(isParagraphRtl, isEnd = false)
            Gravity.END -> relativeAlignment(isParagraphRtl, isEnd = true)
            Gravity.RIGHT -> CapturedHorizontalAlignment.RIGHT
            Gravity.CENTER_HORIZONTAL -> CapturedHorizontalAlignment.CENTER
            else -> CapturedHorizontalAlignment.LEFT
        }
        return CapturedAlignment(horizontal, resolveVerticalAlignment(gravity))
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
