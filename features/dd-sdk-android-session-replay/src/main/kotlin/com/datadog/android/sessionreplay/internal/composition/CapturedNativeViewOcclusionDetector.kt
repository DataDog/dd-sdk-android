/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.utils.GlobalBounds
import kotlin.math.max
import kotlin.math.min

/**
 * Coverage analysis over owned values on the processing executor. Preorder reflects back-to-front
 * drawing; reversing it lets later opaque backgrounds cover earlier visuals across native branches.
 * Structural layers and touch overrides are retained. Unknown geometry, ordering or mapper output
 * never establishes coverage. Windows are independent, including their common root outline clip.
 */
internal class CapturedNativeViewOcclusionDetector(
    private val maxCoveringBounds: Int = MAX_COVERING_BOUNDS,
    private val maxQueryFragments: Int = MAX_QUERY_FRAGMENTS
) {
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned arrays; sizes come from the copied list.
    fun occludedViews(window: CapturedNativeWindow, generation: CaptureGenerationContext): BooleanArray {
        if (!canAnalyze(window)) return NO_OCCLUSION
        val geometry = copyGeometry(window, generation) ?: return NO_OCCLUSION
        val coverage = RectangleCoverage(maxCoveringBounds, maxQueryFragments)
        val occluded = BooleanArray(window.views.size)
        for (index in window.views.indices.reversed()) {
            if (!generation.isActive()) return NO_OCCLUSION
            val rectangle = geometry.rectangles[index]
            if (geometry.reliable[index] && rectangle != null) {
                val covered = coverage.covers(rectangle)
                occluded[index] = covered && window.views[index].mapping.isOcclusionBounded
                if (geometry.opaque[index] && !covered) coverage.add(rectangle)
            }
        }
        return occluded
    }

    private fun canAnalyze(window: CapturedNativeWindow): Boolean {
        if (!window.isDrawingOrderReliable || maxCoveringBounds <= 0 || maxQueryFragments <= 0) return false
        // The root background cannot cover an earlier node; avoid a pass when it is the only candidate.
        var hasCandidate = false
        for (index in 1 until window.views.size) {
            val view = window.views[index]
            if (view.isGeometryReliable && view.pixelBounds != null && view.mapping.hasOpaqueBackground) {
                hasCandidate = true
                break
            }
        }
        return hasCandidate
    }

    private fun copyGeometry(
        window: CapturedNativeWindow,
        generation: CaptureGenerationContext
    ): CoverageGeometry? {
        val geometry = CoverageGeometry(window.views.size)
        for ((index, view) in window.views.withIndex()) {
            if (!generation.isActive() || !geometry.append(view, index)) return null
        }
        return geometry
    }

    private companion object {
        // Hitting either cap can miss an occlusion, but never removes a potentially visible rectangle.
        const val MAX_COVERING_BOUNDS = 64
        const val MAX_QUERY_FRAGMENTS = 64

        @Suppress("UnsafeThirdPartyFunctionCall") // Constant nonnegative array size.
        val NO_OCCLUSION = BooleanArray(0)
    }
}

@Suppress("UnsafeThirdPartyFunctionCall") // Array sizes come from a copied list; parent indices are checked.
private class CoverageGeometry(size: Int) {
    val rectangles = arrayOfNulls<PixelRectangle>(size)
    val reliable = BooleanArray(size)
    val opaque = BooleanArray(size)
    private val childClips = arrayOfNulls<PixelRectangle>(size)
    private val ancestorIsOpaque = BooleanArray(size)

    @Suppress("ReturnCount") // Invalid metadata disables this optional pass; it never establishes coverage.
    fun append(view: CapturedNativeView, index: Int): Boolean {
        val parent = view.parentIndex
        if (parent < -1 || parent >= index) return false
        val bounds = view.pixelBounds?.let(PixelRectangle::from) ?: return true
        reliable[index] = isGeometryReliable(view, parent)
        ancestorIsOpaque[index] = isAncestorOpaque(view, parent)
        val inheritedClip = if (parent < 0) null else childClips[parent]
        var clip = if (view.clipToBounds) inheritedClip.intersectWith(bounds) else inheritedClip
        view.pixelClip?.let {
            val explicit = PixelRectangle.from(it) ?: return false
            clip = clip.intersectWith(explicit)
        }
        rectangles[index] = clip?.intersect(bounds) ?: bounds
        childClips[index] = view.pixelChildPaddingClip?.let {
            val padding = PixelRectangle.from(it) ?: return false
            clip.intersectWith(padding)
        } ?: clip
        opaque[index] = isOpaqueBackground(view, index)
        return true
    }

    private fun isGeometryReliable(view: CapturedNativeView, parent: Int): Boolean =
        view.isGeometryReliable && (parent < 0 || reliable[parent])

    private fun isAncestorOpaque(view: CapturedNativeView, parent: Int): Boolean =
        view.alpha == 1.0 && (parent < 0 || ancestorIsOpaque[parent])

    private fun isOpaqueBackground(view: CapturedNativeView, index: Int): Boolean =
        reliable[index] && ancestorIsOpaque[index] && view.mapping.hasOpaqueBackground
}

private class RectangleCoverage(private val maxBounds: Int, private val maxFragments: Int) {
    private val rectangles = mutableListOf<PixelRectangle>()
    private var totalBounds: PixelRectangle? = null

    // Owned lists; cap exhaustion conservatively retains content.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    fun covers(bounds: PixelRectangle): Boolean {
        if (bounds.isEmpty) return true
        // A union cannot cover anything outside its total extent. This also avoids scanning the
        // retained rectangles for the usual disjoint rows of a list or grid.
        if (totalBounds?.contains(bounds) != true) return false
        var intersects = false
        for (rectangle in rectangles) {
            if (rectangle.contains(bounds)) return true
            intersects = intersects || rectangle.intersects(bounds)
        }
        if (!intersects) return false
        val scratch = arrayOf(mutableListOf(bounds), mutableListOf<PixelRectangle>())
        var current = 0
        for (rectangle in rectangles) {
            val remaining = scratch[current]
            val next = scratch[1 - current]
            next.clear()
            for (fragment in remaining) {
                fragment.subtract(rectangle, next)
                if (next.size > maxFragments) return false
            }
            if (next.isEmpty()) return true
            current = 1 - current
        }
        return false
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned list; index removals run in reverse.
    fun add(bounds: PixelRectangle) {
        if (bounds.isEmpty) return
        val previousBounds = totalBounds
        if (previousBounds?.intersects(bounds) == true) {
            for (index in rectangles.indices.reversed()) {
                if (bounds.contains(rectangles[index])) rectangles.removeAt(index)
            }
        }
        if (rectangles.size < maxBounds) {
            rectangles.add(bounds)
            totalBounds = previousBounds?.union(bounds) ?: bounds
        }
    }
}

private class PixelRectangle(val left: Long, val top: Long, val right: Long, val bottom: Long) {
    val isEmpty: Boolean get() = left >= right || top >= bottom

    fun contains(other: PixelRectangle): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun intersects(other: PixelRectangle): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun union(other: PixelRectangle): PixelRectangle =
        PixelRectangle(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    fun intersect(other: PixelRectangle): PixelRectangle = when {
        contains(other) -> other
        other.contains(this) -> this
        else -> PixelRectangle(
            max(left, other.left),
            max(top, other.top),
            min(right, other.right),
            min(bottom, other.bottom)
        )
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Appends at most four owned rectangles per bounded fragment.
    fun subtract(cover: PixelRectangle, remaining: MutableList<PixelRectangle>) {
        if (!intersects(cover)) {
            remaining.add(this)
            return
        }
        val left = max(this.left, cover.left)
        val top = max(this.top, cover.top)
        val right = min(this.right, cover.right)
        val bottom = min(this.bottom, cover.bottom)
        if (this.top < top) remaining.add(PixelRectangle(this.left, this.top, this.right, top))
        if (bottom < this.bottom) remaining.add(PixelRectangle(this.left, bottom, this.right, this.bottom))
        if (this.left < left) remaining.add(PixelRectangle(this.left, top, left, bottom))
        if (right < this.right) remaining.add(PixelRectangle(right, top, this.right, bottom))
    }

    companion object {
        fun from(bounds: GlobalBounds): PixelRectangle? {
            val width = bounds.width.coerceAtLeast(0)
            val height = bounds.height.coerceAtLeast(0)
            if (bounds.x > Long.MAX_VALUE - width || bounds.y > Long.MAX_VALUE - height) return null
            return PixelRectangle(bounds.x, bounds.y, bounds.x + width, bounds.y + height)
        }
    }
}

private fun PixelRectangle?.intersectWith(bounds: PixelRectangle): PixelRectangle =
    this?.intersect(bounds) ?: bounds
