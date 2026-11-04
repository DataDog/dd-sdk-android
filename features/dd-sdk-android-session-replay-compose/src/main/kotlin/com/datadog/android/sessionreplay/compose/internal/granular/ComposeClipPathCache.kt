/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import java.util.LinkedHashMap

/**
 * Reuses only immutable path strings for copied numeric geometry. Shape/outline and privacy reads
 * remain with the caller on every capture. No UI object or pixels are retained. Called on the UI thread.
 */
internal class ComposeClipPathCache(
    maxEntries: Int = MAX_ENTRIES,
    maxPathLength: Int = MAX_PATH_LENGTH
) {
    private data class Key(
        val rounded: Boolean,
        val first: Long,
        val second: Long,
        val third: Long,
        val fourth: Long,
        val fifth: Long = 0
    )

    private val maxEntries = maxEntries.coerceAtLeast(1)
    private val maxPathLength = maxPathLength.coerceAtLeast(0)

    @Suppress("UnsafeThirdPartyFunctionCall") // Positive initial capacity and load factor for an owned map.
    private val paths = LinkedHashMap<Key, String>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    @Suppress("UnsafeThirdPartyFunctionCall") // toBits copies primitive values; it cannot invoke application code.
    fun rounded(width: Long, height: Long, radius: Double, x: Double, y: Double): String =
        path(Key(true, width, height, radius.toBits(), x.toBits(), y.toBits())) {
            roundedRectPath(width, height, radius, x, y)
        }

    @Suppress("UnsafeThirdPartyFunctionCall") // toBits copies primitive values; it cannot invoke application code.
    fun rectangle(left: Double, top: Double, right: Double, bottom: Double): String =
        path(Key(false, left.toBits(), top.toBits(), right.toBits(), bottom.toBits())) {
            "M $left,$top L $right,$top L $right,$bottom L $left,$bottom Z"
        }

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned bounded map; values and keys contain no UI references.
    private inline fun path(key: Key, create: () -> String): String {
        paths[key]?.let { return it }
        val result = create()
        if (result.length <= maxPathLength) {
            paths[key] = result
            if (paths.size > maxEntries) {
                val oldest = paths.entries.iterator()
                oldest.next()
                oldest.remove()
            }
        }
        return result
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val MAX_ENTRIES = 32
        const val MAX_PATH_LENGTH = 512
    }
}

/** Same formatting as the original clip path, including translated and signed-zero coordinates. */
private fun roundedRectPath(
    width: Long,
    height: Long,
    radius: Double,
    x: Double = 0.0,
    y: Double = 0.0
): String {
    if (x == 0.0 && y == 0.0) {
        val w = width.toDouble()
        val h = height.toDouble()
        return "M $radius,0 " +
            "L ${w - radius},0 A $radius,$radius 0 0 1 $w,$radius " +
            "L $w,${h - radius} A $radius,$radius 0 0 1 ${w - radius},$h " +
            "L $radius,$h A $radius,$radius 0 0 1 0,${h - radius} " +
            "L 0,$radius A $radius,$radius 0 0 1 $radius,0 Z"
    }
    val right = x + width
    val bottom = y + height
    return "M ${x + radius},$y " +
        "L ${right - radius},$y A $radius,$radius 0 0 1 $right,${y + radius} " +
        "L $right,${bottom - radius} A $radius,$radius 0 0 1 ${right - radius},$bottom " +
        "L ${x + radius},$bottom A $radius,$radius 0 0 1 $x,${bottom - radius} " +
        "L $x,${y + radius} A $radius,$radius 0 0 1 ${x + radius},$y Z"
}
