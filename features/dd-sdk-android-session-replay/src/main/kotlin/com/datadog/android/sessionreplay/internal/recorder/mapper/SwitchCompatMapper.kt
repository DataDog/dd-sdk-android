/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Rect
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal open class SwitchCompatMapper(
    private val textWireframeMapper: TextViewMapper,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : CheckableWireframeMapper<SwitchCompat>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    // region CheckableWireframeMapper

    override fun resolveMainWireframes(
        view: SwitchCompat,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        return textWireframeMapper.map(view, mappingContext, asyncJobStatusCallback)
    }

    @Suppress("ReturnCount")
    override fun resolveCheckedCheckable(
        view: SwitchCompat,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        val thumbId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, THUMB_KEY_NAME)
        val trackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, TRACK_KEY_NAME)
        val trackThumbDimensions = resolveThumbAndTrackDimensions(
            view,
            mappingContext.systemInformation
        )
        if (thumbId == null || trackId == null || trackThumbDimensions == null) {
            return null
        }
        val trackWidth = trackThumbDimensions[TRACK_WIDTH_INDEX]
        val trackHeight = trackThumbDimensions[TRACK_HEIGHT_INDEX]
        val thumbHeight = trackThumbDimensions[THUMB_HEIGHT_INDEX]
        val thumbWidth = trackThumbDimensions[THUMB_WIDTH_INDEX]
        val checkableColor = resolveCheckableColor(view)
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val trackShapeStyle = resolveTrackShapeStyle(view, checkableColor)
        val thumbShapeStyle = resolveThumbShapeStyle(view, checkableColor)
        val trackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = trackId,
            x = viewGlobalBounds.x + viewGlobalBounds.width - trackWidth,
            y = viewGlobalBounds.y + (viewGlobalBounds.height - trackHeight) / 2,
            width = trackWidth,
            height = trackHeight,
            border = null,
            shapeStyle = trackShapeStyle
        )
        val thumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = thumbId,
            x = viewGlobalBounds.x + viewGlobalBounds.width - thumbWidth,
            y = viewGlobalBounds.y + (viewGlobalBounds.height - thumbHeight) / 2,
            width = thumbWidth,
            height = thumbHeight,
            border = null,
            shapeStyle = thumbShapeStyle
        )
        return listOf(trackWireframe, thumbWireframe)
    }

    override fun resolveNotCheckedCheckable(
        view: SwitchCompat,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        val thumbId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, THUMB_KEY_NAME)
        val trackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, TRACK_KEY_NAME)
        val trackThumbDimensions = resolveThumbAndTrackDimensions(
            view,
            mappingContext.systemInformation
        )
        if (thumbId == null || trackId == null || trackThumbDimensions == null) {
            return null
        }
        val trackWidth = trackThumbDimensions[TRACK_WIDTH_INDEX]
        val trackHeight = trackThumbDimensions[TRACK_HEIGHT_INDEX]
        val thumbHeight = trackThumbDimensions[THUMB_HEIGHT_INDEX]
        val thumbWidth = trackThumbDimensions[THUMB_WIDTH_INDEX]
        val checkableColor = resolveCheckableColor(view)
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val trackShapeStyle = resolveTrackShapeStyle(view, checkableColor)
        val thumbShapeStyle = resolveThumbShapeStyle(view, checkableColor)
        val trackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = trackId,
            x = viewGlobalBounds.x + viewGlobalBounds.width - trackWidth,
            y = viewGlobalBounds.y + (viewGlobalBounds.height - trackHeight) / 2,
            width = trackWidth,
            height = trackHeight,
            border = null,
            shapeStyle = trackShapeStyle
        )
        val thumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = thumbId,
            x = viewGlobalBounds.x + viewGlobalBounds.width - trackWidth,
            y = viewGlobalBounds.y + (viewGlobalBounds.height - thumbHeight) / 2,
            width = thumbWidth,
            height = thumbHeight,
            border = null,
            shapeStyle = thumbShapeStyle
        )
        return listOf(trackWireframe, thumbWireframe)
    }

    // endregion

    // region Internal

    protected fun resolveCheckableColor(view: SwitchCompat): String {
        return colorStringFormatter.formatColorAndAlphaAsHexString(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    private fun resolveThumbShapeStyle(view: SwitchCompat, checkBoxColor: String): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = checkBoxColor,
            view.alpha,
            cornerRadius = THUMB_CORNER_RADIUS
        )
    }

    protected fun resolveTrackShapeStyle(view: SwitchCompat, checkBoxColor: String): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = checkBoxColor,
            view.alpha
        )
    }

    protected fun resolveThumbAndTrackDimensions(
        view: SwitchCompat,
        systemInformation: SystemInformation
    ): LongArray? {
        val density = systemInformation.screenDensity
        val thumbWidth: Long
        val trackHeight: Long
        // based on the implementation there is nothing drawn in the switcher area if one of
        // these are null
        val thumbDrawable = view.thumbDrawable
        val trackDrawable = view.trackDrawable
        if (thumbDrawable == null || trackDrawable == null) {
            return null
        }
        val paddingRect = Rect()
        thumbDrawable.getPadding(paddingRect)
        val totalHorizontalPadding =
            paddingRect.left.densityNormalized(systemInformation.screenDensity) +
                paddingRect.right.densityNormalized(systemInformation.screenDensity)
        thumbWidth = thumbDrawable.intrinsicWidth.densityNormalized(density).toLong() -
            totalHorizontalPadding
        val thumbHeight: Long = thumbWidth
        // for some reason there is no padding added in the trackDrawable
        // in order to normalise with the padding applied to the width we will have to
        // use the horizontal padding applied.
        trackHeight = trackDrawable.intrinsicHeight.densityNormalized(density).toLong() -
            totalHorizontalPadding
        val trackWidth = thumbWidth * 2
        val dimensions = LongArray(NUMBER_OF_DIMENSIONS)
        dimensions[THUMB_WIDTH_INDEX] = thumbWidth
        dimensions[THUMB_HEIGHT_INDEX] = thumbHeight
        dimensions[TRACK_WIDTH_INDEX] = trackWidth
        dimensions[TRACK_HEIGHT_INDEX] = trackHeight
        return dimensions
    }

    // endregion

    companion object {
        private const val NUMBER_OF_DIMENSIONS = 4
        internal const val THUMB_WIDTH_INDEX = 0
        internal const val THUMB_HEIGHT_INDEX = 1
        internal const val TRACK_WIDTH_INDEX = 2
        internal const val TRACK_HEIGHT_INDEX = 3
        internal const val THUMB_KEY_NAME = "thumb"
        internal const val TRACK_KEY_NAME = "track"
        internal const val THUMB_CORNER_RADIUS = 10
    }
}
