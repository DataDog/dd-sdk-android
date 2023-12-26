/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.model.MobileSegment

internal object BoundsUtils {

    internal fun resolveBounds(wireframe: MobileSegment.Wireframe): WireframeBounds {
        return when (wireframe) {
            is MobileSegment.Wireframe.ShapeWireframe -> wireframe.bounds()
            is MobileSegment.Wireframe.TextWireframe -> wireframe.bounds()
            is MobileSegment.Wireframe.ImageWireframe -> wireframe.bounds()
            is MobileSegment.Wireframe.PlaceholderWireframe -> wireframe.bounds()
            is MobileSegment.Wireframe.WebviewWireframe -> wireframe.bounds()
        }
    }

    internal fun isCovering(
        top: WireframeBounds,
        bottom: WireframeBounds
    ): Boolean {
        return top.left <= bottom.left &&
            top.right >= bottom.right &&
            top.top <= bottom.top &&
            top.bottom >= bottom.bottom
    }

    private fun MobileSegment.Wireframe.ShapeWireframe.bounds(): WireframeBounds {
        return resolveBounds(x, y, width, height, clip)
    }

    private fun MobileSegment.Wireframe.TextWireframe.bounds(): WireframeBounds {
        return resolveBounds(x, y, width, height, clip)
    }
    private fun MobileSegment.Wireframe.ImageWireframe.bounds(): WireframeBounds {
        return resolveBounds(x, y, width, height, clip)
    }

    private fun MobileSegment.Wireframe.PlaceholderWireframe.bounds(): WireframeBounds {
        return resolveBounds(x, y, width, height, clip)
    }
    private fun MobileSegment.Wireframe.WebviewWireframe.bounds(): WireframeBounds {
        return resolveBounds(x, y, width, height, clip)
    }

    private fun resolveBounds(
        x: Long,
        y: Long,
        width: Long,
        height: Long,
        clip: MobileSegment.WireframeClip?
    ): WireframeBounds {
        return WireframeBounds(
            left = x + (clip?.left ?: 0),
            right = x + width - (clip?.right ?: 0),
            top = y + (clip?.top ?: 0),
            bottom = y + height - (clip?.bottom ?: 0),
            width = width,
            height = height
        )
    }
}
