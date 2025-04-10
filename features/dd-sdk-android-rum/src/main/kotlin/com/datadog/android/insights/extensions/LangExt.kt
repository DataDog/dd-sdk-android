/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.extensions

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal val Long.ms: Long
    get() = this / 1_000_000L

internal val Double.Mb: Double
    get() = this / (1024.0 * 1024.0)

internal fun Double?.round(digits: Int): Double {
    if (this == null || isNaN()) return Double.NaN
    val multiplier = 10.0.pow(digits)
    return (this * multiplier).roundToInt() / multiplier
}

internal fun Float.clip(size: Int, min: Int, max: Int): Float {
    return max(min(this, (max - size).toFloat()), min.toFloat())
}

internal fun <F, S> multiLet(first: F?, second: S?, block: (F, S) -> Unit) {
    if (first != null && second != null) block(first, second)
}
