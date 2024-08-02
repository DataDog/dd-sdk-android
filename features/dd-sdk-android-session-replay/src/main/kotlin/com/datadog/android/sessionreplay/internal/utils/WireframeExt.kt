/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.sessionreplay.model.MobileSegment

internal fun MobileSegment.Wireframe.hasOpaqueBackground(): Boolean {
    return when (this) {
        // we return false from ImageWireframe because we don't know if the image is opaque or not
        // and ImageWireframe ShapeStyle is always null
        is MobileSegment.Wireframe.ImageWireframe -> false
        is MobileSegment.Wireframe.ShapeWireframe -> this.hasOpaqueBackground()
        is MobileSegment.Wireframe.TextWireframe -> this.hasOpaqueBackground()
        is MobileSegment.Wireframe.PlaceholderWireframe -> true
        is MobileSegment.Wireframe.WebviewWireframe -> true
    }
}

private fun MobileSegment.Wireframe.ShapeWireframe.hasOpaqueBackground(): Boolean {
    return shapeStyle.isOpaque()
}

private fun MobileSegment.Wireframe.TextWireframe.hasOpaqueBackground(): Boolean {
    return shapeStyle.isOpaque()
}

private fun MobileSegment.ShapeStyle?.isOpaque(): Boolean {
    return this != null && this.isFullyOpaque() && this.hasNonTranslucentColor()
}

internal fun MobileSegment.Wireframe.shapeStyle(): MobileSegment.ShapeStyle? {
    return when (this) {
        is MobileSegment.Wireframe.TextWireframe -> this.shapeStyle
        is MobileSegment.Wireframe.ShapeWireframe -> this.shapeStyle
        is MobileSegment.Wireframe.ImageWireframe -> this.shapeStyle
        is MobileSegment.Wireframe.PlaceholderWireframe -> null
        is MobileSegment.Wireframe.WebviewWireframe -> this.shapeStyle
    }
}

internal fun MobileSegment.Wireframe.copy(shapeStyle: MobileSegment.ShapeStyle?): MobileSegment.Wireframe {
    return when (this) {
        is MobileSegment.Wireframe.TextWireframe -> this.copy(shapeStyle = shapeStyle)
        is MobileSegment.Wireframe.ShapeWireframe -> this.copy(shapeStyle = shapeStyle)
        is MobileSegment.Wireframe.ImageWireframe -> this.copy(shapeStyle = shapeStyle)
        is MobileSegment.Wireframe.PlaceholderWireframe -> this
        is MobileSegment.Wireframe.WebviewWireframe -> this.copy(shapeStyle = shapeStyle)
    }
}
