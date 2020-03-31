/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark.model

import com.google.gson.annotations.SerializedName

data class Benchmark(
    @SerializedName("name") var name: String,
    @SerializedName("className") var className: String,
    @SerializedName("totalRunTimeNs") var totalRunTimeNs: Long,
    @SerializedName("warmupIterations") var warmupIterations: Int,
    @SerializedName("repeatIterations") var repeatIterations: Int,
    @SerializedName("thermalThrottleSleepSeconds") var thermalThrottleSleepSeconds: Int,
    @SerializedName("metrics") var metrics: BenchmarkMetrics
)
