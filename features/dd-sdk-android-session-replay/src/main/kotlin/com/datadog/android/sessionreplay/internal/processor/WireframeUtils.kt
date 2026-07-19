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
        return resolveClip(
            bounds = boundsUtils.resolveBounds(wireframe),
            previousClip = wireframe.clip(),
            ancestorBounds = parents.map { boundsUtils.resolveBounds(it) }
        )
    }

    /**
     * Same ancestor-overflow computation as [resolveWireframeClip], for a caller that already has
     * ancestor bounds on hand rather than a [MobileSegment.Wireframe] list to derive them from —
     * currently only [com.datadog.android.sessionreplay.internal.recorder.CompositionTreeBuilder],
     * which builds a `CompositionLayer`/`CompositionLayerChild` tree, not `Node`/`parents`, so
     * there's no equivalent wireframe list to pass to [resolveWireframeClip] directly. Returns
     * [wireframe]'s own current clip unchanged when [ancestorBounds] is empty (a root-level
     * wireframe with no container above it) — same as [resolveWireframeClip] naturally does for
     * an empty `parents` list.
     */
    internal fun resolveClipFromAncestorBounds(
        wireframe: MobileSegment.Wireframe,
        ancestorBounds: List<WireframeBounds>
    ): MobileSegment.WireframeClip? {
        if (ancestorBounds.isEmpty()) return wireframe.clip()
        return resolveClip(
            bounds = boundsUtils.resolveBounds(wireframe),
            previousClip = wireframe.clip(),
            ancestorBounds = ancestorBounds
        )
    }

    private fun resolveClip(
        bounds: WireframeBounds,
        previousClip: MobileSegment.WireframeClip?,
        ancestorBounds: List<WireframeBounds>
    ): MobileSegment.WireframeClip? {
        var clipTop = previousClip?.top ?: 0L
        var clipLeft = previousClip?.left ?: 0L
        var clipRight = previousClip?.right ?: 0L
        var clipBottom = previousClip?.bottom ?: 0L
        ancestorBounds.forEach {
            clipTop = max(it.top - bounds.top, clipTop)
            clipBottom = max(bounds.bottom - it.bottom, clipBottom)
            clipLeft = max(it.left - bounds.left, clipLeft)
            clipRight = max(bounds.right - it.right, clipRight)
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
            is MobileSegment.Wireframe.WebviewWireframe -> this.clip
        }
    }
}
