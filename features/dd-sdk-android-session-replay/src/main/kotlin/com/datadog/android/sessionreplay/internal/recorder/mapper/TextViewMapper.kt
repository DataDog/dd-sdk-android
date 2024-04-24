/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.AllowObfuscationRule
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.TextValueObfuscationRule
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A [WireframeMapper] implementation to map a [TextView] component.
 * In this case any [TextView] for which the input type is considered sensible (password, email
 * address, postal address, numeric password) will be masked with the static mask: [***].
 * All the other text fields will not be masked.
 */
@Suppress("TooManyFunctions")
open class TextViewMapper internal constructor(
    @Deprecated("") internal var textValueObfuscationRule: TextValueObfuscationRule,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseAsyncBackgroundWireframeMapper<TextView>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    constructor(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ) : this(
        AllowObfuscationRule(),
        viewIdentifierResolver,
        colorStringFormatter,
        viewBoundsResolver,
        drawableToColorMapper
    )

    override fun map(
        view: TextView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        wireframes.addAll(super.map(view, mappingContext, asyncJobStatusCallback))

        val density = mappingContext.systemInformation.screenDensity
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            density
        )

        resolveTextElements(
            view,
            mappingContext,
            viewGlobalBounds
        ).let(wireframes::addAll)

        resolveImages(
            view,
            mappingContext,
            wireframes.size,
            asyncJobStatusCallback
        ).let(wireframes::addAll)

        return wireframes
    }

    // region Internal

    private fun resolveImages(
        textView: TextView,
        mappingContext: MappingContext,
        currentIndex: Int,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        return mappingContext.imageWireframeHelper.createCompoundDrawableWireframes(
            textView,
            mappingContext,
            currentIndex,
            asyncJobStatusCallback
        )
    }

    private fun resolveTextElements(
        view: TextView,
        mappingContext: MappingContext,
        viewGlobalBounds: GlobalBounds
    ): List<MobileSegment.Wireframe> {
        return listOf(
            MobileSegment.Wireframe.TextWireframe(
                id = resolveViewId(view),
                x = viewGlobalBounds.x,
                y = viewGlobalBounds.y,
                width = viewGlobalBounds.width,
                height = viewGlobalBounds.height,
                shapeStyle = null,
                border = null,
                text = textValueObfuscationRule.resolveObfuscatedValue(view, mappingContext),
                textStyle = resolveTextStyle(view, mappingContext.systemInformation.screenDensity),
                textPosition = resolveTextPosition(
                    view,
                    mappingContext.systemInformation.screenDensity
                )
            )
        )
    }

    private fun resolveTextStyle(textView: TextView, pixelsDensity: Float): MobileSegment.TextStyle {
        return MobileSegment.TextStyle(
            resolveFontFamily(textView.typeface),
            textView.textSize.toLong().densityNormalized(pixelsDensity),
            resolveTextColor(textView)
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
        return when {
            typeface === Typeface.SANS_SERIF -> SANS_SERIF_FAMILY_NAME
            typeface === Typeface.MONOSPACE -> MONOSPACE_FAMILY_NAME
            typeface === Typeface.SERIF -> SERIF_FAMILY_NAME
            else -> SANS_SERIF_FAMILY_NAME
        }
    }

    private fun resolveTextPosition(textView: TextView, pixelsDensity: Float): MobileSegment.TextPosition {
        return MobileSegment.TextPosition(
            resolvePadding(textView, pixelsDensity),
            resolveAlignment(textView)
        )
    }

    private fun resolvePadding(textView: TextView, pixelsDensity: Float): MobileSegment.Padding {
        return MobileSegment.Padding(
            top = textView.totalPaddingTop.densityNormalized(pixelsDensity).toLong(),
            bottom = textView.totalPaddingBottom.densityNormalized(pixelsDensity).toLong(),
            left = textView.totalPaddingStart.densityNormalized(pixelsDensity).toLong(),
            right = textView.totalPaddingEnd.densityNormalized(pixelsDensity).toLong()
        )
    }

    private fun resolveAlignment(textView: TextView): MobileSegment.Alignment {
        return when (textView.textAlignment) {
            TextView.TEXT_ALIGNMENT_CENTER -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.CENTER,
                vertical = MobileSegment.Vertical.CENTER
            )

            TextView.TEXT_ALIGNMENT_TEXT_END,
            TextView.TEXT_ALIGNMENT_VIEW_END -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.RIGHT,
                vertical = MobileSegment.Vertical.CENTER
            )

            TextView.TEXT_ALIGNMENT_TEXT_START,
            TextView.TEXT_ALIGNMENT_VIEW_START -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.LEFT,
                vertical = MobileSegment.Vertical.CENTER
            )

            TextView.TEXT_ALIGNMENT_GRAVITY -> resolveAlignmentFromGravity(textView)
            else -> MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.LEFT,
                vertical = MobileSegment.Vertical.CENTER
            )
        }
    }

    private fun resolveAlignmentFromGravity(textView: TextView): MobileSegment.Alignment {
        val horizontalAlignment = when (textView.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK)) {
            Gravity.START,
            Gravity.LEFT -> MobileSegment.Horizontal.LEFT

            Gravity.END,
            Gravity.RIGHT -> MobileSegment.Horizontal.RIGHT

            Gravity.CENTER -> MobileSegment.Horizontal.CENTER
            Gravity.CENTER_HORIZONTAL -> MobileSegment.Horizontal.CENTER
            else -> MobileSegment.Horizontal.LEFT
        }
        val verticalAlignment = when (textView.gravity.and(Gravity.VERTICAL_GRAVITY_MASK)) {
            Gravity.TOP -> MobileSegment.Vertical.TOP
            Gravity.BOTTOM -> MobileSegment.Vertical.BOTTOM
            Gravity.CENTER_VERTICAL -> MobileSegment.Vertical.CENTER
            Gravity.CENTER -> MobileSegment.Vertical.CENTER
            else -> MobileSegment.Vertical.CENTER
        }

        return MobileSegment.Alignment(horizontalAlignment, verticalAlignment)
    }

    // endregion

    internal companion object {
        internal const val STATIC_MASK = "***"
        internal const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
        internal const val SERIF_FAMILY_NAME = "serif"
        internal const val MONOSPACE_FAMILY_NAME = "monospace"
    }
}
