/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.sessionreplay.utils.GlobalBounds
import kotlin.math.max
import kotlin.math.min

/**
 * Coverage analysis over owned values on the construction executor. Preorder reflects back-to-front
 * drawing; reversing it lets later opaque backgrounds cover earlier visuals across native branches.
 * Structural layers and touch overrides are retained. Unknown geometry, ordering or mapper output
 * never establishes coverage. Windows are independent, including their common root outline clip.
 */
internal class CapturedNativeViewOcclusionDetector(
    private val maxCoveringBounds: Int = MAX_COVERING_BOUNDS,
    private val maxQueryFragments: Int = MAX_QUERY_FRAGMENTS
) {
    @Suppress("ReturnCount") // Expiry conservatively disables this optional pass.
    fun occludedViews(window: CapturedNativeWindow, generation: CaptureGenerationContext): BooleanArray {
        if (!canAnalyze(window)) return NO_OCCLUSION
        val pixels = resolveCoverageGeometry(window, generation, replayCoordinates = false) ?: return NO_OCCLUSION
        val replay = resolveCoverageGeometry(window, generation, replayCoordinates = true) ?: return NO_OCCLUSION
        val coverage = WindowCoverage(maxCoveringBounds, maxQueryFragments)

        @Suppress("UnsafeThirdPartyFunctionCall") // Nonnegative copied list size.
        val occluded = BooleanArray(window.views.size)
        for (index in window.views.indices.reversed()) {
            if (!generation.isActive()) return NO_OCCLUSION
            occluded[index] = coverage.isOccluded(window.views[index], index, pixels, replay)
        }
        return occluded
    }

    private fun canAnalyze(window: CapturedNativeWindow): Boolean {
        if (!window.isDrawingOrderReliable || maxCoveringBounds <= 0 || maxQueryFragments <= 0) return false
        // The root background cannot cover an earlier node; avoid a pass when it is the only candidate.
        var hasCandidate = false
        for (index in 1 until window.views.size) {
            val view = window.views[index]
            if (view.isGeometryReliable && view.pixelBounds != null && view.viewData.hasOpaqueBackground) {
                hasCandidate = true
                break
            }
        }
        return hasCandidate
    }

    private fun resolveCoverageGeometry(
        window: CapturedNativeWindow,
        generation: CaptureGenerationContext,
        replayCoordinates: Boolean
    ): CoverageGeometry? {
        val geometry = CoverageGeometry(window.views.size)
        for ((index, view) in window.views.withIndex()) {
            if (!generation.isActive() || !geometry.append(view, index, replayCoordinates)) return null
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

private class WindowCoverage(maxBounds: Int, maxFragments: Int) {
    private val pixels = RectangleCoverage(maxBounds, maxFragments)
    private val replay = RectangleCoverage(maxBounds, maxFragments)

    @Suppress("ReturnCount") // Missing or uncertain geometry conservatively retains content.
    fun isOccluded(view: CapturedNativeView, index: Int, pixels: CoverageGeometry, replay: CoverageGeometry): Boolean {
        if (!pixels.reliable[index] || !replay.reliable[index]) return false
        val pixelRectangle = pixels.rectangles[index] ?: return false
        val replayRectangle = replay.rectangles[index] ?: return false
        return coversView(view, pixelRectangle, replayRectangle, pixels.opaque[index])
    }

    private fun coversView(
        view: CapturedNativeView,
        pixelRectangle: CoverageRectangle,
        replayRectangle: CoverageRectangle,
        opaque: Boolean
    ): Boolean {
        val pixelsCovered = pixels.covers(pixelRectangle)
        val replayCovered = replay.covers(replayRectangle)
        if (opaque) {
            if (!pixelsCovered) pixels.add(pixelRectangle)
            if (!replayCovered) replay.add(replayRectangle)
        }
        // Origin and size normalize independently. Coverage must hold in both representations.
        return pixelsCovered && replayCovered && view.viewData.isOcclusionBounded
    }
}

private class CoverageGeometry(size: Int) {
    @Suppress("UnsafeThirdPartyFunctionCall") // Nonnegative copied list size.
    val rectangles = arrayOfNulls<CoverageRectangle>(size)

    @Suppress("UnsafeThirdPartyFunctionCall") // Nonnegative copied list size.
    val reliable = BooleanArray(size)

    @Suppress("UnsafeThirdPartyFunctionCall") // Nonnegative copied list size.
    val opaque = BooleanArray(size)

    @Suppress("UnsafeThirdPartyFunctionCall") // Nonnegative copied list size.
    private val childClips = arrayOfNulls<CoverageRectangle>(size)

    @Suppress("UnsafeThirdPartyFunctionCall") // Nonnegative copied list size.
    private val ancestorIsOpaque = BooleanArray(size)

    @Suppress("ReturnCount") // Invalid metadata disables this optional pass; it never establishes coverage.
    fun append(view: CapturedNativeView, index: Int, replayCoordinates: Boolean): Boolean {
        val parent = view.parentIndex
        if (parent < -1 || parent >= index) return false
        if (view.pixelBounds == null) return true
        val bounds = resolveBounds(view, replayCoordinates) ?: return false
        reliable[index] = isGeometryReliable(view, parent)
        ancestorIsOpaque[index] = isAncestorOpaque(view, parent)
        val inheritedClip = if (parent < 0) null else childClips[parent]
        var clip = if (view.clipToBounds) inheritedClip.intersectWith(bounds) else inheritedClip
        val explicit = resolveExplicitClip(view, replayCoordinates)
        val padding = resolvePaddingClip(view, replayCoordinates)
        if (!hasValidClips(view, replayCoordinates, explicit, padding)) return false
        if (explicit != null) clip = clip.intersectWith(explicit)
        rectangles[index] = clip?.intersect(bounds) ?: bounds
        childClips[index] = padding?.let { clip.intersectWith(it) } ?: clip
        opaque[index] = isOpaqueBackground(view, index)
        return true
    }

    private fun hasValidClips(
        view: CapturedNativeView,
        replayCoordinates: Boolean,
        explicit: CoverageRectangle?,
        padding: CoverageRectangle?
    ): Boolean {
        val hasExplicit = if (replayCoordinates) view.explicitClip != null else view.pixelClip != null
        val hasPadding = if (replayCoordinates) view.childPaddingClip != null else view.pixelChildPaddingClip != null
        return (!hasExplicit || explicit != null) && (!hasPadding || padding != null)
    }

    private fun resolveBounds(view: CapturedNativeView, replayCoordinates: Boolean): CoverageRectangle? =
        if (replayCoordinates) CoverageRectangle.from(view.bounds) else view.pixelBounds?.let(CoverageRectangle::from)

    private fun resolveExplicitClip(view: CapturedNativeView, replayCoordinates: Boolean): CoverageRectangle? =
        if (replayCoordinates) {
            view.explicitClip?.let(CoverageRectangle::from)
        } else {
            view.pixelClip?.let(CoverageRectangle::from)
        }

    private fun resolvePaddingClip(view: CapturedNativeView, replayCoordinates: Boolean): CoverageRectangle? =
        if (replayCoordinates) {
            view.childPaddingClip?.let(CoverageRectangle::from)
        } else {
            view.pixelChildPaddingClip?.let(CoverageRectangle::from)
        }

    private fun isGeometryReliable(view: CapturedNativeView, parent: Int): Boolean =
        view.isGeometryReliable && (parent < 0 || reliable[parent])

    private fun isAncestorOpaque(view: CapturedNativeView, parent: Int): Boolean =
        view.alpha == 1.0 && (parent < 0 || ancestorIsOpaque[parent])

    private fun isOpaqueBackground(view: CapturedNativeView, index: Int): Boolean =
        reliable[index] && ancestorIsOpaque[index] && view.viewData.hasOpaqueBackground
}

private class RectangleCoverage(private val maxBounds: Int, private val maxFragments: Int) {
    private val rectangles = mutableListOf<CoverageRectangle>()
    private var totalBounds: CoverageRectangle? = null

    @Suppress("ReturnCount") // Cap exhaustion conservatively retains content.
    fun covers(bounds: CoverageRectangle): Boolean {
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
        val scratch = arrayOf(mutableListOf(bounds), mutableListOf<CoverageRectangle>())
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

    fun add(bounds: CoverageRectangle) {
        if (bounds.isEmpty) return
        val previousBounds = totalBounds
        if (previousBounds?.intersects(bounds) == true) {
            for (index in rectangles.indices.reversed()) {
                if (bounds.contains(rectangles[index])) rectangles.removeAt(index)
            }
        }
        if (rectangles.size < maxBounds) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Owned mutable list below its size cap.
            rectangles.add(bounds)
            totalBounds = previousBounds?.union(bounds) ?: bounds
        }
    }
}

private class CoverageRectangle(val left: Long, val top: Long, val right: Long, val bottom: Long) {
    val isEmpty: Boolean get() = left >= right || top >= bottom

    fun contains(other: CoverageRectangle): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun intersects(other: CoverageRectangle): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun union(other: CoverageRectangle): CoverageRectangle =
        CoverageRectangle(
            min(left, other.left),
            min(top, other.top),
            max(right, other.right),
            max(bottom, other.bottom)
        )

    fun intersect(other: CoverageRectangle): CoverageRectangle = when {
        contains(other) -> other
        other.contains(this) -> this
        else -> CoverageRectangle(
            max(left, other.left),
            max(top, other.top),
            min(right, other.right),
            min(bottom, other.bottom)
        )
    }

    fun subtract(cover: CoverageRectangle, remaining: MutableList<CoverageRectangle>) {
        if (!intersects(cover)) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Appends an owned fragment to a mutable list.
            remaining.add(this)
            return
        }
        val left = max(this.left, cover.left)
        val top = max(this.top, cover.top)
        val right = min(this.right, cover.right)
        val bottom = min(this.bottom, cover.bottom)
        if (this.top < top) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Appends an owned fragment to a mutable list.
            remaining.add(CoverageRectangle(this.left, this.top, this.right, top))
        }
        if (bottom < this.bottom) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Appends an owned fragment to a mutable list.
            remaining.add(CoverageRectangle(this.left, bottom, this.right, this.bottom))
        }
        if (this.left < left) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Appends an owned fragment to a mutable list.
            remaining.add(CoverageRectangle(this.left, top, left, bottom))
        }
        if (right < this.right) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Appends an owned fragment to a mutable list.
            remaining.add(CoverageRectangle(right, top, this.right, bottom))
        }
    }

    companion object {
        fun from(bounds: GlobalBounds): CoverageRectangle? = from(bounds.x, bounds.y, bounds.width, bounds.height)

        fun from(bounds: CapturedBounds): CoverageRectangle? = from(bounds.x, bounds.y, bounds.width, bounds.height)

        private fun from(x: Long, y: Long, rawWidth: Long, rawHeight: Long): CoverageRectangle? {
            val width = rawWidth.coerceAtLeast(0)
            val height = rawHeight.coerceAtLeast(0)
            if (x > Long.MAX_VALUE - width || y > Long.MAX_VALUE - height) return null
            return CoverageRectangle(x, y, x + width, y + height)
        }
    }
}

private fun CoverageRectangle?.intersectWith(bounds: CoverageRectangle): CoverageRectangle =
    this?.intersect(bounds) ?: bounds
