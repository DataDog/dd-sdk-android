/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment

internal fun MobileSegment.Wireframe.bounds(): Bounds {
    return when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> this.bounds()
        is MobileSegment.Wireframe.TextWireframe -> this.bounds()
    }
}

internal fun MobileSegment.Wireframe.ShapeWireframe.bounds(): Bounds {
    return Bounds(x, y, width, height)
}

internal fun MobileSegment.Wireframe.TextWireframe.bounds(): Bounds {
    return Bounds(x, y, width, height)
}

internal data class Bounds(val x: Long, val y: Long, val width: Long, val height: Long)
