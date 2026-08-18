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

/** Bounded rectangle coverage; exhausting either cap conservatively retains content. */
internal class RectangleCoverage(private val maxBounds: Int, private val maxFragments: Int) {
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
            // Removing higher indices leaves every upcoming lower index unchanged.
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

internal class CoverageRectangle(val left: Long, val top: Long, val right: Long, val bottom: Long) {
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
