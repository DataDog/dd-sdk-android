/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

internal data class VitalInfo(
    val sampleCount: Int,
    val minValue: Double,
    val maxValue: Double,
    val meanValue: Double
) {
    companion object {
        val EMPTY = VitalInfo(0, Double.MAX_VALUE, -Double.MAX_VALUE, 0.0)
    }
}
