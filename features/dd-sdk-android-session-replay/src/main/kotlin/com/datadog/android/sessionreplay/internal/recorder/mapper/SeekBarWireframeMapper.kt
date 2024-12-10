/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.SeekBar
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import kotlin.math.max

internal open class SeekBarWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : ProgressBarWireframeMapper<SeekBar>(
    viewIdentifierResolver = viewIdentifierResolver,
    colorStringFormatter = colorStringFormatter,
    viewBoundsResolver = viewBoundsResolver,
    drawableToColorMapper = drawableToColorMapper,
    showProgressWhenMaskUserInput = false
) {

    override fun mapDeterminate(
        wireframes: MutableList<MobileSegment.Wireframe>,
        view: SeekBar,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger,
        trackBounds: GlobalBounds,
        trackColor: Int,
        normalizedProgress: Float
    ) {
        super.mapDeterminate(
            wireframes,
            view,
            mappingContext,
            asyncJobStatusCallback,
            internalLogger,
            trackBounds,
            trackColor,
            normalizedProgress
        )

        if (mappingContext.textAndInputPrivacy == TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
            val screenDensity = mappingContext.systemInformation.screenDensity
            val trackHeight = ProgressBarWireframeMapper.TRACK_HEIGHT_IN_PX.densityNormalized(screenDensity)
            val thumbColor = getColor(view.thumbTintList, view.drawableState) ?: getDefaultColor(view)

            buildThumbWireframe(
                view = view,
                trackBounds = trackBounds,
                normalizedProgress = normalizedProgress,
                trackHeight = trackHeight,
                screenDensity = screenDensity,
                thumbColor = thumbColor
            )?.let(wireframes::add)
        }
    }

    private fun buildThumbWireframe(
        view: SeekBar,
        trackBounds: GlobalBounds,
        normalizedProgress: Float,
        trackHeight: Long,
        screenDensity: Float,
        thumbColor: Int
    ): MobileSegment.Wireframe? {
        val thumbId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, THUMB_KEY_NAME)
            ?: return null
        val backgroundColor = colorStringFormatter.formatColorAndAlphaAsHexString(thumbColor, OPAQUE_ALPHA_VALUE)

        val thumbWidth = view.thumb.bounds.width().densityNormalized(screenDensity).toLong()
        val thumbHeight = view.thumb.bounds.height().densityNormalized(screenDensity).toLong()
        return MobileSegment.Wireframe.ShapeWireframe(
            id = thumbId,
            x = (trackBounds.x + (trackBounds.width * normalizedProgress).toLong() - (thumbWidth / 2)),
            y = trackBounds.y + (trackHeight / 2) - (thumbHeight / 2),
            width = thumbWidth,
            height = thumbHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = backgroundColor,
                opacity = view.alpha,
                cornerRadius = max(thumbWidth / 2, thumbHeight / 2)
            )
        )
    }

    companion object {
        internal const val NIGHT_MODE_COLOR = 0xffffff // White
        internal const val DAY_MODE_COLOR = 0 // Black
        internal const val ACTIVE_TRACK_KEY_NAME = "seekbar_active_track"
        internal const val NON_ACTIVE_TRACK_KEY_NAME = "seekbar_non_active_track"
        internal const val THUMB_KEY_NAME = "seekbar_thumb"

        internal const val THUMB_SHAPE_CORNER_RADIUS = 10
        internal const val TRACK_HEIGHT_IN_PX = 8L
    }
}
