/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min

/**
 * A memory-optimal index mapping: given a targeted relative accuracy, it requires the least number
 * of indices to cover a given range of values. This is done by logarithmically mapping
 * floating-point values to integers.
 */
internal class LogarithmicMapping(requestedRelativeAccuracy: Double) {
    private val gamma: Double
    private val multiplier: Double

    val relativeAccuracy: Double
    val minIndexedValue: Double
    val maxIndexedValue: Double

    init {
        val validRequestedAccuracy = requestedRelativeAccuracy.coerceIn(
            MIN_SAFE_RELATIVE_ACCURACY,
            MAX_SAFE_RELATIVE_ACCURACY
        )

        /*
         * Calculates the (minimal) base that needs to be used for the mapping to be relatively accurate
         * with the provided relative accuracy.
         */
        gamma = (1 + validRequestedAccuracy) / (1 - validRequestedAccuracy)

        multiplier = 1.0 / ln1p(gamma - 1) // ln(e) = 1, so Math.log(Math.E) / Math.log1p(x) simplifies to 1.0 / ln1p(x)
        relativeAccuracy = (gamma - 1) / (gamma + 1)

        minIndexedValue = max(
            exp(Int.MIN_VALUE / multiplier + 1), // so that index >= Integer.MIN_VALUE
            java.lang.Double.MIN_NORMAL * gamma
        )
        maxIndexedValue = min(
            exp(Int.MAX_VALUE / multiplier - 1), // so that index <= Integer.MAX_VALUE
            Double.MAX_VALUE / (1 + relativeAccuracy)
        )
    }

    fun getIndexFor(value: Double): Int {
        val index = ln(value) * multiplier
        return if (index >= 0) index.toInt() else index.toInt() - 1 // faster than floor
    }

    fun value(index: Int): Double = lowerBound(index) * (1 + relativeAccuracy)

    fun lowerBound(index: Int): Double = exp(index / multiplier)

    fun serializedSize(): Int {
        return DDSketchSerializer.doubleFieldSize(FIELD_GAMMA, gamma)
        // FIELD_INDEX_OFFSET (2): 0.0 is proto3 default, omitted
        // FIELD_INTERPOLATION (3): NONE (0) is proto3 default, omitted
    }

    fun writeTo(serializer: DDSketchSerializer) {
        serializer.writeDouble(FIELD_GAMMA, gamma)
        // FIELD_INDEX_OFFSET (2): 0.0 is proto3 default, omitted
        // FIELD_INTERPOLATION (3): NONE (0) is proto3 default, omitted
    }

    companion object {
        /** Conservative lower bound. Smaller values produce impractically large bin counts. */
        const val MIN_SAFE_RELATIVE_ACCURACY = 0.0001

        /** Upper clamp; generous bound — production callers typically use 0.01. */
        const val MAX_SAFE_RELATIVE_ACCURACY = 0.9999

        // Proto field numbers from IndexMapping message in DDSketch.proto
        private const val FIELD_GAMMA = 1
    }
}
