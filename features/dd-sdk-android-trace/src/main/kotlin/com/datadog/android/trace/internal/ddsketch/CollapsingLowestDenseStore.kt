/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal.ddsketch

import kotlin.math.max
import kotlin.math.min

/**
 * A dense, count-collapsing store for DDSketch bin counts.
 *
 * Counts are held in a contiguous array indexed by bin number. When a new index would push
 * the active range wider than `maxNumBins`, the store collapses: all counts for indices
 * below the new feasible minimum are merged into the lowest remaining bin, and any future index
 * below `minIndex` is routed there. This preserves total count but loses precision
 * for the lowest quantiles.
 */
@Suppress("TooManyFunctions")
internal class CollapsingLowestDenseStore(private val maxNumBins: Int) {
    private var isCollapsed = false
    private var counts = DoubleArray(0)
    private var offset = 0

    var minIndex: Int = Int.MAX_VALUE
        private set
    var maxIndex: Int = Int.MIN_VALUE
        private set

    val isEmpty: Boolean
        get() = maxIndex < minIndex

    val totalCount: Double
        get() = counts.sum()

    fun add(index: Int) {
        val binIndex = findBinIndex(index)
        counts[binIndex]++
    }

    @Suppress("ReturnCount")
    private fun findBinIndex(index: Int): Int {
        if (index < minIndex) {
            if (isCollapsed) {
                return 0 // all below-minimum indices route to the collapsed catch-all bin if already collapsed
            } else {
                extendRange(index)
                if (isCollapsed) {
                    return 0 // extendRange triggered collapse; index is below the new minimum
                }
            }
        } else if (index > maxIndex) {
            extendRange(index)
        }

        return index - offset
    }

    private fun extendRange(index: Int) {
        val newMinIndex = min(index, minIndex)
        val newMaxIndex = max(index, maxIndex)

        if (isEmpty) {
            val initialLength = capacityForRange(newMinIndex, newMaxIndex)
            if (initialLength >= counts.size) {
                counts = DoubleArray(initialLength)
            }
            offset = newMinIndex
            minIndex = newMinIndex
            maxIndex = newMaxIndex
            adjust(newMinIndex, newMaxIndex)
        } else if (newMinIndex >= offset && newMaxIndex < offset.toLong() + counts.size) {
            minIndex = newMinIndex
            maxIndex = newMaxIndex
        } else {
            val newLength = capacityForRange(newMinIndex, newMaxIndex)
            if (newLength > counts.size) {
                @Suppress("UnsafeThirdPartyFunctionCall") // newLength is always non-negative
                counts = counts.copyOf(newLength)
            }
            adjust(newMinIndex, newMaxIndex)
        }
    }

    private fun adjust(newMinIndex: Int, newMaxIndex: Int) {
        if (newMaxIndex.toLong() - newMinIndex + 1 > counts.size) {
            val adjustedMin = newMaxIndex - counts.size + 1

            if (adjustedMin >= maxIndex) {
                val total = totalCount
                resetCounts(minIndex, maxIndex) // non-empty store guarantees minIndex <= maxIndex
                offset = adjustedMin
                minIndex = adjustedMin
                counts[0] = total
            } else {
                val shift = offset - adjustedMin
                if (shift < 0) {
                    val collapsedCount = getTotalCount(minIndex, adjustedMin - 1)
                    resetCounts(minIndex, adjustedMin - 1)
                    counts[adjustedMin - offset] += collapsedCount
                    minIndex = adjustedMin
                    shiftCounts(shift)
                } else {
                    shiftCounts(shift)
                    minIndex = adjustedMin
                }
            }

            maxIndex = newMaxIndex
            isCollapsed = true
        } else {
            centerCounts(newMinIndex, newMaxIndex)
        }
    }

    private fun shiftCounts(shift: Int) {
        val minArrayIndex = minIndex - offset
        val maxArrayIndex = maxIndex - offset
        @Suppress("UnsafeThirdPartyFunctionCall") // callers compute shift so the dest range stays within the array
        counts.copyInto(counts, minArrayIndex + shift, minArrayIndex, maxArrayIndex + 1)
        if (shift > 0) {
            @Suppress("UnsafeThirdPartyFunctionCall") // shift >= 1 so toIndex > fromIndex
            counts.fill(0.0, minArrayIndex, minArrayIndex + shift)
        } else {
            @Suppress("UnsafeThirdPartyFunctionCall") // shift <= 0 so toIndex >= fromIndex
            counts.fill(0.0, maxArrayIndex + 1 + shift, maxArrayIndex + 1)
        }
        offset -= shift
    }

    private fun centerCounts(newMinIndex: Int, newMaxIndex: Int) {
        val middleIndex = newMinIndex + (newMaxIndex - newMinIndex + 1) / 2
        shiftCounts(offset + counts.size / 2 - middleIndex)
        minIndex = newMinIndex
        maxIndex = newMaxIndex
    }

    private fun resetCounts(fromIndex: Int, toIndex: Int) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Callers guarantee fromIndex <= toIndex
        counts.fill(0.0, fromIndex - offset, toIndex - offset + 1)
    }

    /**
     * Returns the target array length for the given index range, rounded up to the nearest
     * multiple of [DEFAULT_ARRAY_LENGTH_GROWTH_INCREMENT] with a small overhead to reduce
     * reallocations, capped at [maxNumBins].
     */
    private fun capacityForRange(newMinIndex: Int, newMaxIndex: Int): Int {
        val desiredLength = newMaxIndex.toLong() - newMinIndex + 1
        val uncapped =
            ((desiredLength + DEFAULT_ARRAY_LENGTH_OVERHEAD - 1) / DEFAULT_ARRAY_LENGTH_GROWTH_INCREMENT + 1) *
                DEFAULT_ARRAY_LENGTH_GROWTH_INCREMENT
        return min(uncapped, maxNumBins.toLong()).toInt()
    }

    private fun getTotalCount(fromIndex: Int, toIndex: Int): Double {
        if (isEmpty) {
            return 0.0
        }

        val fromArrayIndex = max(fromIndex - offset, 0)
        val toArrayIndex = min(toIndex - offset, counts.size - 1)
        return (fromArrayIndex..toArrayIndex).sumOf { counts[it] }
    }

    fun forEach(action: (index: Int, count: Double) -> Unit) {
        if (isEmpty) return

        for (i in minIndex..maxIndex) {
            val count = counts[i - offset]
            if (count != 0.0) {
                action(i, count)
            }
        }
    }

    fun bins(ascending: Boolean): Sequence<Pair<Int, Double>> {
        if (isEmpty) {
            return emptySequence()
        }

        val indices = if (ascending) {
            (minIndex..maxIndex)
        } else {
            (maxIndex downTo minIndex)
        }
        return indices.asSequence()
            .filter { counts[it - offset] != 0.0 }
            .map { it to counts[it - offset] }
    }

    fun serializedSize(): Int {
        return if (isEmpty) {
            0
        } else {
            DDSketchSerializer.sizeOfCompactDoubleArray(FIELD_CONTIGUOUS_BIN_COUNTS, maxIndex - minIndex + 1) +
                DDSketchSerializer.signedIntFieldSize(FIELD_CONTIGUOUS_BIN_INDEX_OFFSET, minIndex)
        }
    }

    fun writeTo(serializer: DDSketchSerializer) {
        if (isEmpty) return

        // FIELD_BIN_COUNTS (1) is sparsely encoded bins and we don't use that format
        serializer.writeCompactArray(FIELD_CONTIGUOUS_BIN_COUNTS, counts, minIndex - offset, maxIndex - minIndex + 1)
        serializer.writeSignedInt32(FIELD_CONTIGUOUS_BIN_INDEX_OFFSET, minIndex)
    }

    private companion object {
        private const val DEFAULT_ARRAY_LENGTH_GROWTH_INCREMENT = 64
        private const val DEFAULT_ARRAY_LENGTH_OVERHEAD_RATIO = 0.1
        private const val DEFAULT_ARRAY_LENGTH_OVERHEAD =
            (DEFAULT_ARRAY_LENGTH_GROWTH_INCREMENT * DEFAULT_ARRAY_LENGTH_OVERHEAD_RATIO).toInt()

        // Proto field numbers from Store message in DDSketch.proto
        private const val FIELD_CONTIGUOUS_BIN_COUNTS = 2
        private const val FIELD_CONTIGUOUS_BIN_INDEX_OFFSET = 3
    }
}
