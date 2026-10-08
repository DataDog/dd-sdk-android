/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.drawable.ColorDrawable
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper

/**
 * Finds children in a resolved drawing order that are fully painted over by a later (higher z-order) opaque
 * sibling and therefore contribute nothing to the final image - mapping them, including any
 * pixel-fallback [View.draw] capture, would be wasted work.
 *
 * Conservative by construction: only a single sibling's rect is checked per child (no rect-union
 * covering), and candidates with rotation, scale, outline clipping or restrictive clip bounds are excluded.
 * Missing a real occlusion only costs performance; uncertain coverage must preserve potentially
 * visible views.
 */
internal class ViewOcclusionDetector(
    private val drawableToColorMapper: DrawableToColorMapper,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val internalLogger: InternalLogger
) {

    fun occludedChildIndices(drawingOrder: ViewGroupDrawingOrder): Set<Int> {
        val children = drawingOrder.children
        val childCount = children.size
        // Culling is optional. Bound its quadratic preprocessing instead of delaying the next yield.
        if (childCount !in 2..MAX_OCCLUSION_CHILDREN || !drawingOrder.isReliable) return emptySet()
        val occluded = mutableSetOf<Int>()
        val coveringBoundsAbove = mutableListOf<SiblingBounds>()
        for (i in childCount - 1 downTo 0) {
            val child = children[i]
            // A transformed child cannot be represented by its layout rectangle in either direction.
            if (hasScaleOrRotation(child)) continue
            val childBounds = boundsInParent(child)
            if (coveringBoundsAbove.any { it.fullyCovers(childBounds) }) {
                @Suppress("UnsafeThirdPartyFunctionCall") // plain HashSet-backed add, cannot throw
                occluded.add(i)
            }
            if (isFullyOpaqueCovering(child)) {
                @Suppress("UnsafeThirdPartyFunctionCall") // plain ArrayList-backed add, cannot throw
                coveringBoundsAbove.add(childBounds)
            }
        }
        return occluded
    }

    private fun boundsInParent(view: View): SiblingBounds {
        // Siblings share ancestor transforms and scrolling. Compare before those transforms,
        // keeping fractional pixel translations instead of rounding screen bounds to density units.
        val left = view.left.toDouble() + view.translationX
        val top = view.top.toDouble() + view.translationY
        return SiblingBounds(left, top, left + view.width, top + view.height)
    }

    private class SiblingBounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        fun fullyCovers(other: SiblingBounds): Boolean =
            left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
    }

    private fun isFullyOpaqueCovering(view: View): Boolean {
        // A representative color does not prove coverage for inset, rounded or custom drawables.
        val color = (view.background as? ColorDrawable)?.let {
            drawableToColorMapper.mapDrawableToColor(it, internalLogger)
        } ?: return false
        // Same extraction as android.graphics.Color.alpha(), inlined so this doesn't depend on the
        // Android framework's own (unavailable-in-unit-tests) implementation for a one-line shift.
        val alpha = (color ushr ALPHA_SHIFT) and ALPHA_MASK
        return alpha == FULLY_OPAQUE_ALPHA &&
            !viewUtilsInternal.isNotVisible(view) &&
            view.alpha == 1f &&
            // An outline may leave corners or other parts of the rectangular bounds uncovered.
            !view.clipToOutline &&
            !hasRestrictiveClipBounds(view)
    }

    private fun hasScaleOrRotation(view: View): Boolean =
        view.rotation != 0f || view.rotationX != 0f || view.rotationY != 0f ||
            view.scaleX != 1f || view.scaleY != 1f

    private fun hasRestrictiveClipBounds(view: View): Boolean {
        val clip = view.clipBounds ?: return false
        // clipBounds is local and in pixels, just like width/height. A partial (or empty) clip
        // means the opaque background cannot guarantee coverage of the full local rectangle.
        val clipsHorizontally = clip.left > 0 || clip.right < view.width
        val clipsVertically = clip.top > 0 || clip.bottom < view.height
        return clipsHorizontally || clipsVertically
    }

    private companion object {
        // At most 2,016 rectangle comparisons per group; larger groups retain every child.
        const val MAX_OCCLUSION_CHILDREN = 64
        const val ALPHA_SHIFT = 24
        const val ALPHA_MASK = 0xff
        const val FULLY_OPAQUE_ALPHA = 0xff
    }
}
