/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class MaskSwitchCompatMapper(
    textWireframeMapper: TextViewMapper,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : SwitchCompatMapper(
    textWireframeMapper,
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    // region CheckableWireframeMapper

    override fun resolveCheckedCheckable(
        view: SwitchCompat,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        return resolveNotCheckedCheckable(view, mappingContext)
    }

    override fun resolveNotCheckedCheckable(
        view: SwitchCompat,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        val trackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, TRACK_KEY_NAME)
        val trackThumbDimensions = resolveThumbAndTrackDimensions(
            view,
            mappingContext.systemInformation
        )
        if (trackId == null || trackThumbDimensions == null) {
            return null
        }
        val trackWidth = trackThumbDimensions[TRACK_WIDTH_INDEX]
        val trackHeight = trackThumbDimensions[TRACK_HEIGHT_INDEX]
        val checkableColor = resolveCheckableColor(view)
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val trackShapeStyle = resolveTrackShapeStyle(view, checkableColor)
        val trackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = trackId,
            x = viewGlobalBounds.x + viewGlobalBounds.width - trackWidth,
            y = viewGlobalBounds.y + (viewGlobalBounds.height - trackHeight) / 2,
            width = trackWidth,
            height = trackHeight,
            border = null,
            shapeStyle = trackShapeStyle
        )
        return listOf(trackWireframe)
    }

    // endregion
}
