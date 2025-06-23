/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.benchmark_converter.models

import com.datadog.android.benchmark_converter.models.CBMFResult.Measurement
import kotlinx.serialization.Serializable

typealias CBMFMetric = Map<String, Measurement>
typealias CBMFRun = Map<String, Measurement>

@Serializable
data class CBMFResult(
    val schema_version: String,
    val benchmarks: List<Benchmark>
) {


    @Serializable
    data class Benchmark(
        val parameters: Map<String, String>, // scenario is required
        val runs: Map<String, CBMFRun>,
    )

    @Serializable
    data class Measurement(
        val uom: String,
        val values: List<Double>
    )
}
