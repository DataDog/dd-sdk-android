/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal class MaskAllSwitchCompatMapper(
    textWireframeMapper: TextViewMapper,
    stringUtils: StringUtils = StringUtils,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils
) : SwitchCompatMapper(textWireframeMapper, stringUtils, uniqueIdentifierGenerator, viewUtils) {

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
        val trackId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, TRACK_KEY_NAME)
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
        val viewGlobalBounds = resolveViewGlobalBounds(
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
