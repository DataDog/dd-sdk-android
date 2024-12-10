/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A [WireframeMapper] implementation to map a [TextView] component.
 */
@Suppress("TooManyFunctions")
open class TextViewMapper<in T : TextView>(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseAsyncBackgroundWireframeMapper<T>(
    viewIdentifierResolver = viewIdentifierResolver,
    colorStringFormatter = colorStringFormatter,
    viewBoundsResolver = viewBoundsResolver,
    drawableToColorMapper = drawableToColorMapper
) {

    @UiThread
    override fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        // add background if needed
        wireframes.addAll(super.map(view, mappingContext, asyncJobStatusCallback, internalLogger))

        val density = mappingContext.systemInformation.screenDensity
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view = view,
            screenDensity = density
        )

        wireframes.add(
            createTextWireframe(
                textView = view,
                mappingContext = mappingContext,
                viewGlobalBounds = viewGlobalBounds
            )
        )

        wireframes.addAll(
            mappingContext.imageWireframeHelper.createCompoundDrawableWireframes(
                textView = view,
                mappingContext = mappingContext,
                prevWireframeIndex = wireframes.size,
                customResourceIdCacheKey = null,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        )

        return wireframes
    }

    // region Abstract

    /**
     * Resolves the text to record for this TextView.
     * @param textView the textView being mapped
     * @param textAndInputPrivacy the current text and input privacy setting
     * @param isOption whether the textview is part of an option menu
     */
    protected open fun resolveCapturedText(
        textView: T,
        textAndInputPrivacy: TextAndInputPrivacy,
        isOption: Boolean
    ): String {
        val originalText = resolveLayoutText(textView)
        return when (textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> originalText
            TextAndInputPrivacy.MASK_ALL -> if (isOption) {
                FIXED_INPUT_MASK
            } else {
                StringObfuscator.getStringObfuscator().obfuscate(originalText)
            }

            TextAndInputPrivacy.MASK_ALL_INPUTS -> if (isOption) FIXED_INPUT_MASK else originalText
        }
    }

    // endregion

    // region Internal

    private fun resolveLayoutText(textView: T): String {
        return (textView.layout?.text ?: textView.text)?.toString().orEmpty()
    }

    /**
     * Creates text wireframe for the captured text.
     * @param textView the textView being mapped.
     * @param mappingContext the mapping context.
     * @param viewGlobalBounds global bounds of the view.
     */
    protected fun createTextWireframe(
        textView: T,
        mappingContext: MappingContext,
        viewGlobalBounds: GlobalBounds
    ): MobileSegment.Wireframe.TextWireframe {
        val capturedText = resolveCapturedText(
            textView,
            mappingContext.textAndInputPrivacy,
            mappingContext.hasOptionSelectorParent
        )
        return MobileSegment.Wireframe.TextWireframe(
            id = resolveViewId(textView),
            x = viewGlobalBounds.x,
            y = viewGlobalBounds.y,
            width = viewGlobalBounds.width,
            height = viewGlobalBounds.height,
            shapeStyle = null,
            border = null,
            text = capturedText,
            textStyle = resolveTextStyle(textView, mappingContext.systemInformation.screenDensity),
            textPosition = resolveTextPosition(
                textView,
                mappingContext.systemInformation.screenDensity
            )
        )
    }

    private fun resolveTextStyle(textView: T, pixelsDensity: Float): MobileSegment.TextStyle {
        return MobileSegment.TextStyle(
            resolveFontFamily(textView.typeface),
            textView.textSize.toLong().densityNormalized(pixelsDensity),
            resolveTextColor(textView)
        )
    }

    private fun resolveTextColor(textView: T): String {
        return if (textView.text.isNullOrEmpty()) {
            resolveHintTextColor(textView)
        } else {
            colorStringFormatter.formatColorAndAlphaAsHexString(textView.currentTextColor, OPAQUE_ALPHA_VALUE)
        }
    }

    private fun resolveHintTextColor(textView: T): String {
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
        internal const val FIXED_INPUT_MASK = "***"
        internal const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
        internal const val SERIF_FAMILY_NAME = "serif"
        internal const val MONOSPACE_FAMILY_NAME = "monospace"
    }
}
