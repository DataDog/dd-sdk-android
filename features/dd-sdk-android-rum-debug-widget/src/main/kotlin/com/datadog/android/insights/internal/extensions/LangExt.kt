/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.internal.extensions

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import com.datadog.android.insights.internal.widgets.ChartView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal const val NANOS_PER_MILLI: Long = 1_000_000L
internal const val BYTES_PER_MB: Long = 1024 * 1024

internal val Long.ms: Long
    get() = this / NANOS_PER_MILLI

internal val Double.Mb: Double
    get() = this / BYTES_PER_MB

internal fun Double?.round(digits: Int): Double {
    val multiplier = 10.0.pow(digits)
    if (this == null || isNaN() || this > Double.MAX_VALUE / multiplier) return Double.NaN

    return (this * multiplier).roundToInt() / multiplier
}

internal fun Float.clip(size: Int, min: Int, max: Int): Float {
    return max(min(this, (max - size).toFloat()), min.toFloat())
}

internal fun <F, S> multiLet(first: F?, second: S?, block: (F, S) -> Unit) {
    if (first != null && second != null) block(first, second)
}

internal fun SpannableStringBuilder.appendColored(text: String, color: Int): SpannableStringBuilder = apply {
    if (text.isEmpty()) return@apply

    val offset = length

    @Suppress("UnsafeThirdPartyFunctionCall")
    // Safe because: character span (not paragraph), non-empty text, valid bounds
    append(text).setSpan(ForegroundColorSpan(color), offset, offset + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

internal fun View.setupChartView(
    @IdRes id: Int,
    labelText: String,
    enableChart: Boolean = true
): ChartView {
    return findViewById<ChartView>(id)
        .also { chart ->
            chart.label = labelText
            chart.chartEnabled = enableChart
        }
}

internal fun View.px(dp: Int): Float = (dp * context.resources.displayMetrics.density)

internal fun View.color(@ColorRes id: Int) = ContextCompat.getColor(context, id)
