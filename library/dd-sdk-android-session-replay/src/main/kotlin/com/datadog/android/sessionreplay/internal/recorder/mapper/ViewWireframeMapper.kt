/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment

internal class ViewWireframeMapper :
    BaseWireframeMapper<View, MobileSegment.Wireframe.ShapeWireframe>() {

    override fun map(view: View, pixelsDensity: Float): List<MobileSegment.Wireframe.ShapeWireframe> {
        val viewGlobalBounds = resolveViewGlobalBounds(view, pixelsDensity)
        val styleBorderPair = view.background?.resolveShapeStyleAndBorder(view.alpha)
        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                resolveViewId(view),
                viewGlobalBounds.x,
                viewGlobalBounds.y,
                viewGlobalBounds.width,
                viewGlobalBounds.height,
                shapeStyle = styleBorderPair?.first,
                border = styleBorderPair?.second
            )
        )
    }
}
