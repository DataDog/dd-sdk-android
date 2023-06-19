/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.sessionreplay.model.MobileSegment

internal fun MobileSegment.Wireframe.hasOpaqueBackground(): Boolean {
    val shapeStyle = this.shapeStyle()
    return shapeStyle != null && shapeStyle.isFullyOpaque() && shapeStyle.hasNonTranslucentColor()
}

internal fun MobileSegment.Wireframe.shapeStyle(): MobileSegment.ShapeStyle? {
    return when (this) {
        is MobileSegment.Wireframe.TextWireframe -> this.shapeStyle
        is MobileSegment.Wireframe.ShapeWireframe -> this.shapeStyle
        is MobileSegment.Wireframe.ImageWireframe -> this.shapeStyle
    }
}

internal fun MobileSegment.Wireframe.copy(shapeStyle: MobileSegment.ShapeStyle?):
    MobileSegment.Wireframe {
    return when (this) {
        is MobileSegment.Wireframe.TextWireframe -> this.copy(shapeStyle = shapeStyle)
        is MobileSegment.Wireframe.ShapeWireframe -> this.copy(shapeStyle = shapeStyle)
        is MobileSegment.Wireframe.ImageWireframe -> this.copy(shapeStyle = shapeStyle)
    }
}
