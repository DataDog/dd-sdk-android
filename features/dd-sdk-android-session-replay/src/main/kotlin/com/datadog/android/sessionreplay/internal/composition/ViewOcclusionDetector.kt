/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Finds children of a [ViewGroup] that are fully painted over by a later (higher z-order) opaque
 * sibling and therefore contribute nothing to the final image - mapping them, including any
 * pixel-fallback [View.draw] capture, would be wasted work.
 *
 * Conservative by construction: only a single sibling's rect is checked per child (no rect-union
 * covering), and a sibling only counts as covering when it has no rotation/scale, so its
 * axis-aligned bounds always match what it actually paints. Missing a real occlusion just costs
 * performance; the opposite - wrongly culling a still-visible view - never happens.
 */
internal class ViewOcclusionDetector(
    private val viewBoundsResolver: ViewBoundsResolver,
    private val drawableToColorMapper: DrawableToColorMapper,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val internalLogger: InternalLogger
) {

    fun occludedChildIndices(viewGroup: ViewGroup, screenDensity: Float): Set<Int> {
        val childCount = viewGroup.childCount
        if (childCount < 2) return emptySet()
        val occluded = mutableSetOf<Int>()
        val coveringBoundsAbove = mutableListOf<CapturedBounds>()
        for (i in childCount - 1 downTo 0) {
            val child = viewGroup.getChildAt(i) ?: continue
            val childBounds = viewBoundsResolver.resolveViewGlobalBounds(child, screenDensity).toCaptured()
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

    private fun CapturedBounds.fullyCovers(other: CapturedBounds): Boolean =
        x <= other.x && y <= other.y &&
            x + width >= other.x + other.width &&
            y + height >= other.y + other.height

    private fun isFullyOpaqueCovering(view: View): Boolean {
        val color = view.background?.let { drawableToColorMapper.mapDrawableToColor(it, internalLogger) }
            ?: return false
        // Same extraction as android.graphics.Color.alpha(), inlined so this doesn't depend on the
        // Android framework's own (unavailable-in-unit-tests) implementation for a one-line shift.
        val alpha = (color ushr ALPHA_SHIFT) and ALPHA_MASK
        return alpha == FULLY_OPAQUE_ALPHA &&
            !viewUtilsInternal.isNotVisible(view) &&
            view.alpha == 1f &&
            view.rotation == 0f && view.rotationX == 0f && view.rotationY == 0f &&
            view.scaleX == 1f && view.scaleY == 1f
    }

    private companion object {
        const val ALPHA_SHIFT = 24
        const val ALPHA_MASK = 0xff
        const val FULLY_OPAQUE_ALPHA = 0xff
    }
}
