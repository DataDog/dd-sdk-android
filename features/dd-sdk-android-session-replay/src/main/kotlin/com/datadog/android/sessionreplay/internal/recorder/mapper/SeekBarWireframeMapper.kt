/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@RequiresApi(Build.VERSION_CODES.O)
internal open class SeekBarWireframeMapper(
    private val viewUtils: ViewUtils = ViewUtils,
    private val stringUtils: StringUtils = StringUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator =
        UniqueIdentifierGenerator
) : BaseWireframeMapper<SeekBar, MobileSegment.Wireframe>(stringUtils, viewUtils) {

    @Suppress("LongMethod")
    override fun map(view: SeekBar, mappingContext: MappingContext):
        List<MobileSegment.Wireframe> {
        val activeTrackId = uniqueIdentifierGenerator
            .resolveChildUniqueIdentifier(view, TRACK_ACTIVE_KEY_NAME)
        val nonActiveTrackId = uniqueIdentifierGenerator
            .resolveChildUniqueIdentifier(view, TRACK_NON_ACTIVE_KEY_NAME)
        val thumbId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, THUMB_KEY_NAME)

        if (activeTrackId == null || thumbId == null || nonActiveTrackId == null) {
            return emptyList()
        }

        val screenDensity = mappingContext.systemInformation.screenDensity
        val viewGlobalBounds = viewUtils.resolveViewGlobalBounds(view, screenDensity)
        val normalizedSliderValue = view.normalizedValue()
        val viewAlpha = view.alpha

        // padding
        val trackLeftPadding = view.trackLeftPadding(screenDensity)
        val trackTopPadding = view.paddingTop.toLong().densityNormalized(screenDensity)

        // colors
        val trackActiveColor = view.getTrackColor()
        val thumbColor = view.getThumbColor()
        val trackActiveColorAsHexa = stringUtils.formatColorAndAlphaAsHexa(
            trackActiveColor,
            OPAQUE_ALPHA_VALUE
        )
        val trackNonActiveColorAsHexa = stringUtils.formatColorAndAlphaAsHexa(
            trackActiveColor,
            PARTIALLY_OPAQUE_ALPHA_VALUE
        )
        val thumbColorAsHexa = stringUtils.formatColorAndAlphaAsHexa(thumbColor, OPAQUE_ALPHA_VALUE)

        // track dimensions
        val trackBounds = view.progressDrawable.bounds
        val trackWidth = trackBounds.width().toLong().densityNormalized(screenDensity)
        val trackHeight = TRACK_HEIGHT_IN_PX.densityNormalized(screenDensity)
        val trackActiveWidth = (trackWidth * normalizedSliderValue).toLong()

        // track positions
        val trackXPos = viewGlobalBounds.x + trackLeftPadding
        val trackYPos = viewGlobalBounds.y + trackTopPadding +
            (viewGlobalBounds.height - trackHeight) / 2

        // thumb dimensions
        val thumbBounds = view.thumb.bounds
        val thumbHeight = thumbBounds.height().toLong().densityNormalized(screenDensity)

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

        return resolveViewAsWireframesList(
            trackNonActiveWireframe,
            trackActiveWireframe,
            thumbWireframe
        )
    }

    protected open fun resolveViewAsWireframesList(
        nonActiveTrackWireframe: MobileSegment.Wireframe.ShapeWireframe,
        activeTrackWireframe: MobileSegment.Wireframe.ShapeWireframe,
        thumbWireframe: MobileSegment.Wireframe.ShapeWireframe
    ): List<MobileSegment.Wireframe> {
        return listOf(nonActiveTrackWireframe, activeTrackWireframe, thumbWireframe)
    }

    private fun SeekBar.trackLeftPadding(screenDensity: Float): Long {
        return this.paddingStart.toLong().densityNormalized(screenDensity)
    }

    private fun SeekBar.normalizedValue(): Float {
        return (this.progress.toFloat() - min.toFloat()) / (max.toFloat() - min.toFloat())
    }

    private fun SeekBar.getTrackColor(): Int {
        return this.progressTintList?.getColor(this.drawableState) ?: getDefaultColor()
    }

    private fun SeekBar.getThumbColor(): Int {
        return this.thumbTintList?.getColor(this.drawableState) ?: getDefaultColor()
    }

    private fun ColorStateList.getColor(state: IntArray): Int {
        return getColorForState(state, defaultColor)
    }

    private fun SeekBar.getDefaultColor(): Int {
        val uiModeFlags = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return if (uiModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            NIGHT_MODE_COLOR
        } else {
            DAY_MODE_COLOR
        }
    }

    companion object {
        internal const val NIGHT_MODE_COLOR = 0xffffff // White
        internal const val DAY_MODE_COLOR = 0 // Black
        internal const val TRACK_ACTIVE_KEY_NAME = "seekbar_active_track"
        internal const val TRACK_NON_ACTIVE_KEY_NAME = "seekbar_non_active_track"
        internal const val THUMB_KEY_NAME = "seekbar_thumb"
        internal const val OPAQUE_ALPHA_VALUE: Int = 0xff
        internal const val PARTIALLY_OPAQUE_ALPHA_VALUE: Int = 0x44
        internal const val THUMB_SHAPE_CORNER_RADIUS = 10
        internal const val TRACK_HEIGHT_IN_PX = 8L
    }
}
