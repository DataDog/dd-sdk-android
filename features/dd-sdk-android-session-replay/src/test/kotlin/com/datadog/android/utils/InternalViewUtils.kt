/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

internal fun Int.isCloseToOrGreaterThan(compareTo: Int): Boolean {
    return this > compareTo || isCloseTo(compareTo, this)
}
internal fun Int.isCloseToOrLessThan(compareTo: Int): Boolean {
    return this < compareTo || isCloseTo(compareTo, this)
}

// add tolerance to mitigate flakiness in tests with Long casting
internal fun isCloseTo(firstItem: Int, secondItem: Int, tolerance: Double = 0.01): Boolean {
    require(tolerance in 0.0..1.0) { "Tolerance must be between 0 and 1" }

    val delta = firstItem * tolerance
    val min: Int = (firstItem - delta).toInt()
    val max: Int = (firstItem + delta).toInt()
    return secondItem in (min..max)
}
