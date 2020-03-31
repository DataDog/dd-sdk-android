/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark.model

import com.google.gson.annotations.SerializedName

data class BenchmarkContext(
    @SerializedName("build") var targetBuild: BenchmarkTargetBuild,
    @SerializedName("cpuCoreCount") var cpuCoreCount: Int,
    @SerializedName("cpuLocked") var cpuLocked: Boolean,
    @SerializedName("cpuMaxFreqHz") var cpuMaxFreqHz: Int,
    @SerializedName("memTotalBytes") var memTotalBytes: Long,
    @SerializedName("sustainedPerformanceModeEnabled") var sustainedPerformanceModeEnabled: Boolean
)
