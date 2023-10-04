/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class ViewWireframeMapper :
    BaseWireframeMapper<View, MobileSegment.Wireframe.ShapeWireframe>() {

    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe.ShapeWireframe> {
        val viewGlobalBounds = resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val (shapeStyle, border) = view.background?.resolveShapeStyleAndBorder(view.alpha)
            ?: (null to null)
        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                resolveViewId(view),
                viewGlobalBounds.x,
                viewGlobalBounds.y,
                viewGlobalBounds.width,
                viewGlobalBounds.height,
                shapeStyle = shapeStyle,
                border = border
            )
        )
    }
}
