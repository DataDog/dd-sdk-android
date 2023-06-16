/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.internal.utils.hasOpaqueBackground
import com.datadog.android.sessionreplay.model.MobileSegment
import kotlin.math.max

internal class WireframeUtils {

    internal fun resolveWireframeClip(
        wireframe: MobileSegment.Wireframe,
        parents: List<MobileSegment.Wireframe>
    ): MobileSegment.WireframeClip? {
        var clipTop = 0L
        var clipLeft = 0L
        var clipRight = 0L
        var clipBottom = 0L
        val wireframeBounds = wireframe.bounds()

        parents.map { it.bounds() }.forEach {
            clipTop = max(it.top - wireframeBounds.top, clipTop)
            clipBottom = max(wireframeBounds.bottom - it.bottom, clipBottom)
            clipLeft = max(it.left - wireframeBounds.left, clipLeft)
            clipRight = max(wireframeBounds.right - it.right, clipRight)
        }

        @Suppress("ComplexCondition")
        return if (clipTop > 0 || clipBottom > 0 || clipLeft > 0 || clipRight > 0) {
            MobileSegment.WireframeClip(
                top = clipTop,
                bottom = clipBottom,
                left = clipLeft,
                right = clipRight
            )
        } else {
            null
        }
    }

    internal fun checkWireframeIsCovered(
        wireframe: MobileSegment.Wireframe,
        topWireframes: List<MobileSegment.Wireframe>
    ): Boolean {
        val wireframeBounds = wireframe.bounds()
        topWireframes.forEach {
            if (it.bounds().isCovering(wireframeBounds) && it.hasOpaqueBackground()) {
                return true
            }
        }
        return false
    }

    internal fun checkWireframeIsValid(wireframe: MobileSegment.Wireframe): Boolean {
        val wireframeBounds = wireframe.bounds()
        return (
            wireframeBounds.width > 0 &&
                wireframeBounds.height > 0 &&
                !(
                    wireframe is MobileSegment.Wireframe.ShapeWireframe &&
                        wireframe.shapeStyle == null &&
                        wireframe.border == null
                    )
            )
    }

    private fun Bounds.isCovering(other: Bounds): Boolean {
        return left <= other.left &&
            right >= other.right &&
            top <= other.top &&
            bottom >= other.bottom
    }

    private fun MobileSegment.Wireframe.bounds(): Bounds {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.bounds()
            is MobileSegment.Wireframe.TextWireframe -> this.bounds()
            is MobileSegment.Wireframe.ImageWireframe -> this.bounds()
        }
    }

    private fun MobileSegment.Wireframe.ShapeWireframe.bounds(): Bounds {
        return Bounds(
            left = x + (clip?.left ?: 0),
            right = x + width - (clip?.right ?: 0),
            top = y + (clip?.top ?: 0),
            bottom = y + height - (clip?.bottom ?: 0),
            width = width,
            height = height
        )
    }

    private fun MobileSegment.Wireframe.TextWireframe.bounds(): Bounds {
        return Bounds(
            left = x + (clip?.left ?: 0),
            right = x + width - (clip?.right ?: 0),
            top = y + (clip?.top ?: 0),
            bottom = y + height - (clip?.bottom ?: 0),
            width = width,
            height = height
        )
    }

    internal data class Bounds(
        val left: Long,
        val right: Long,
        val top: Long,
        val bottom: Long,
        val width: Long,
        val height: Long
    )
}
