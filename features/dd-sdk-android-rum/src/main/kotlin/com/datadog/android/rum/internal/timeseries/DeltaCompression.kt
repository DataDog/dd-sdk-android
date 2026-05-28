/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.timeseries

import com.google.gson.JsonArray
import kotlin.math.roundToLong

internal object DeltaCompression {

    fun roundToLongSafely(value: Double, replaceNaNWith: Long = 0L, scale: Long = SCALE): Long = try {
        (value * scale).roundToLong()
    } catch (_: IllegalArgumentException) {
        replaceNaNWith
    }

    fun <T> List<T>.mapToDeltaCompressed(extractor: (T) -> Long) = JsonArray(size).also {
        for (i in indices) {
            @Suppress("UnsafeThirdPartyFunctionCall") // NoSuchElementException could not be thrown here
            it.add(if (i == 0) extractor(get(i)) else extractor(get(i)) - extractor(get(i - 1)))
        }
    }

    const val PRECISION = 4
    const val SCALE: Long = 10_000L // 10^4
}
