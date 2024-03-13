/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal data class Box(
    private val left: Long,
    private val top: Long,
    private val right: Long,
    private val bottom: Long
) {

    val x: Long
        get() = left

    val y: Long
        get() = top

    val width: Long
        get() = right - left

    val height: Long
        get() = bottom - top

    fun withDensity(density: Float): Box {
        return Box(
            left = (left / density).toLong(),
            top = (top / density).toLong(),
            right = (right / density).toLong(),
            bottom = (bottom / density).toLong()
        )
    }

    private fun union(other: Box): Box {
        return Box(
            left = min(left, other.left),
            top = min(top, other.top),
            bottom = max(bottom, other.bottom),
            right = max(right, other.right)
        )
    }

    companion object {

        fun from(compositionGroup: CompositionGroup, depth: Int = 0): Box? {
            val node = compositionGroup.node
            return if (node is LayoutInfo) {
                from(node)
            } else {
                compositionGroup.compositionGroups.mapNotNull {
                    from(it, depth + 1)
                }.reduceOrNull { acc, box -> box.union(acc) }
            }
        }

        private fun from(layoutInfo: LayoutInfo): Box {
            return if (!layoutInfo.isAttached) {
                // An unattached node has no position
                Box(0, 0, layoutInfo.width.toLong(), layoutInfo.height.toLong())
            } else {
                val position = layoutInfo.coordinates.positionInWindow()
                val size = layoutInfo.coordinates.size
                val left = position.x.roundToLong()
                val top = position.y.roundToLong()
                val right = left + size.width
                val bottom = top + size.height
                Box(left = left, top = top, right = right, bottom = bottom)
            }
        }
    }
}
