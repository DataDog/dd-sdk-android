/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.internal.profiler.BenchmarkSdkPerformance

private const val TRACK_NAME = "track"
private const val METER_NAME = "dd-sdk-android"

internal fun sendBenchmarkTelemetry(
    benchmarkSdkPerformance: BenchmarkSdkPerformance,
    featureName: String,
    metricName: String,
    value: Long
) {
    val tags = mapOf(
        TRACK_NAME to featureName
    )

    benchmarkSdkPerformance
        .getMeter(METER_NAME)
        .getCounter(metricName)
        .add(value, tags)
}
