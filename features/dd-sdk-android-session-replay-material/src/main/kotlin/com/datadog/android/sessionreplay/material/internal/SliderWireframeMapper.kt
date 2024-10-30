/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material.internal

import android.content.res.ColorStateList
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.PARTIALLY_OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.slider.Slider

internal open class SliderWireframeMapper(
    private val viewIdentifierResolver: ViewIdentifierResolver,
    private val colorStringFormatter: ColorStringFormatter,
    private val viewBoundsResolver: ViewBoundsResolver
) : WireframeMapper<Slider> {

    @Suppress("LongMethod")
    @UiThread
    override fun map(
        view: Slider,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val activeTrackId = viewIdentifierResolver
            .resolveChildUniqueIdentifier(view, TRACK_ACTIVE_KEY_NAME)
        val nonActiveTrackId = viewIdentifierResolver
            .resolveChildUniqueIdentifier(view, TRACK_NON_ACTIVE_KEY_NAME)
        val thumbId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, THUMB_KEY_NAME)

        if (activeTrackId == null || thumbId == null || nonActiveTrackId == null) {
            return emptyList()
        }

        val screenDensity = mappingContext.systemInformation.screenDensity
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, screenDensity)
        val normalizedSliderValue = view.normalizedValue()
        val viewAlpha = view.alpha

        // padding
        val trackLeftPadding = view.trackLeftPadding(screenDensity)
        val trackTopPadding = view.paddingTop.toLong().densityNormalized(screenDensity)

        // colors
        val drawableState = view.drawableState
        val trackActiveColor = view.trackActiveTintList.getColor(drawableState)
        val trackNonActiveColor = view.trackInactiveTintList.getColor(drawableState)
        val thumbColor = view.thumbTintList.getColor(drawableState)
        val trackActiveColorAsHexa = colorStringFormatter.formatColorAndAlphaAsHexString(
            trackActiveColor,
            OPAQUE_ALPHA_VALUE
        )
        val trackNonActiveColorAsHexa = colorStringFormatter.formatColorAndAlphaAsHexString(
            trackNonActiveColor,
            PARTIALLY_OPAQUE_ALPHA_VALUE
        )
        val thumbColorAsHexa = colorStringFormatter.formatColorAndAlphaAsHexString(thumbColor, OPAQUE_ALPHA_VALUE)

        // track dimensions
        val trackWidth = view.trackWidth.toLong().densityNormalized(screenDensity)
        val trackHeight = view.trackHeight.toLong().densityNormalized(screenDensity)
        val trackActiveWidth = (trackWidth * normalizedSliderValue).toLong()

        // track positions
        val trackXPos = viewGlobalBounds.x + trackLeftPadding
        val trackYPos = viewGlobalBounds.y + trackTopPadding +
            (viewGlobalBounds.height - trackHeight) / 2

        // thumb dimensions
        val thumbHeight = (view.thumbRadius.toLong().densityNormalized(screenDensity)) * 2

        // thumb positions
        val thumbXPos = (trackXPos + trackWidth * normalizedSliderValue).toLong()
        val thumbYPos = viewGlobalBounds.y + trackTopPadding +
            (viewGlobalBounds.height - thumbHeight) / 2

        val trackNonActiveWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = nonActiveTrackId,
            x = trackXPos,
            y = trackYPos,
            width = trackWidth,
            height = trackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = trackNonActiveColorAsHexa,
                opacity = viewAlpha
            )
        )
        val trackActiveWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = activeTrackId,
            x = trackXPos,
            y = trackYPos,
            width = trackActiveWidth,
            height = trackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = trackActiveColorAsHexa,
                opacity = viewAlpha
            )
        )
        val thumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = thumbId,
            x = thumbXPos,
            y = thumbYPos,
            width = thumbHeight,
            height = thumbHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = thumbColorAsHexa,
                opacity = viewAlpha,
                cornerRadius = THUMB_SHAPE_CORNER_RADIUS
            )
        )

        return if (mappingContext.textAndInputPrivacy == TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
            listOf(trackNonActiveWireframe, trackActiveWireframe, thumbWireframe)
        } else {
            listOf(trackNonActiveWireframe)
        }
    }

    private fun ColorStateList.getColor(state: IntArray): Int {
        return getColorForState(state, defaultColor)
    }

    private fun Slider.trackLeftPadding(screenDensity: Float): Long {
        return this.trackSidePadding.toLong().densityNormalized(screenDensity) +
            this.paddingStart.toLong().densityNormalized(screenDensity)
    }

    private fun Slider.normalizedValue(): Float {
        val range = valueTo - valueFrom
        return if (range == 0f) {
            0f
        } else {
            (value - valueFrom) / range
        }
    }

    companion object {
        internal const val TRACK_ACTIVE_KEY_NAME = "slider_active_track"
        internal const val TRACK_NON_ACTIVE_KEY_NAME = "slider_non_active_track"
        internal const val THUMB_KEY_NAME = "slider_thumb"
        internal const val THUMB_SHAPE_CORNER_RADIUS = 10
    }
}
