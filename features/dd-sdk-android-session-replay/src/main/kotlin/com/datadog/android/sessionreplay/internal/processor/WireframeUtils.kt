/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.internal.utils.hasOpaqueBackground
import com.datadog.android.sessionreplay.model.MobileSegment
import kotlin.math.max

internal class WireframeUtils(private val boundsUtils: BoundsUtils = BoundsUtils) {

    internal fun resolveWireframeClip(
        wireframe: MobileSegment.Wireframe,
        parents: List<MobileSegment.Wireframe>
    ): MobileSegment.WireframeClip? {
        val previousClip = wireframe.clip()
        var clipTop = previousClip?.top ?: 0L
        var clipLeft = previousClip?.left ?: 0L
        var clipRight = previousClip?.right ?: 0L
        var clipBottom = previousClip?.bottom ?: 0L
        val wireframeBounds = boundsUtils.resolveBounds(wireframe)
        parents.map { boundsUtils.resolveBounds(it) }.forEach {
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
        val wireframeBounds = boundsUtils.resolveBounds(wireframe)
        topWireframes.forEach {
            val topBounds = boundsUtils.resolveBounds(it)
            if (boundsUtils.isCovering(topBounds, wireframeBounds) &&
                it.hasOpaqueBackground()
            ) {
                return true
            }
        }
        return false
    }

    internal fun checkWireframeIsValid(wireframe: MobileSegment.Wireframe): Boolean {
        val wireframeBounds = boundsUtils.resolveBounds(wireframe)
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

    private fun MobileSegment.Wireframe.clip(): MobileSegment.WireframeClip? {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.clip
            is MobileSegment.Wireframe.TextWireframe -> this.clip
            is MobileSegment.Wireframe.ImageWireframe -> this.clip
            is MobileSegment.Wireframe.PlaceholderWireframe -> this.clip
        }
    }
}
