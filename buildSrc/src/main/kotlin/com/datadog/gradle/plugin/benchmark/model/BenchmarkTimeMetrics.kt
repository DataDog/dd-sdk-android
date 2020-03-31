/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark.model

import com.google.gson.annotations.SerializedName

data class BenchmarkTimeMetrics(
    @SerializedName("minimum") var minimum: Long,
    @SerializedName("maximum") var maximum: Long,
    @SerializedName("median") var median: Long,
    @SerializedName("runs") var runs: List<Long>
)
