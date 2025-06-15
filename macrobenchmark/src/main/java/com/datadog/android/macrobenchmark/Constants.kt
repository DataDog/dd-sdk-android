/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupTimingMetric

@OptIn(ExperimentalMetricApi::class)
internal val DEFAULT_METRICS_LIST = listOf(
    StartupTimingMetric(),
    MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max),
    FrameTimingMetric()
)