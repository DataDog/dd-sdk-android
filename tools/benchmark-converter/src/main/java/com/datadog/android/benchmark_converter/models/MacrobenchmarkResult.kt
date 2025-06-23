/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.benchmark_converter.models

import kotlinx.serialization.Serializable

@Serializable
data class MacrobenchmarkResult(
    val context: Context,
    val benchmarks: List<Benchmark>
) {
    @Serializable
    data class Context(
        val build: Build,
        val cpuCoreCount: Int,
        val cpuLocked: Boolean,
        val cpuMaxFreqHz: Long,
        val memTotalBytes: Long,
        val sustainedPerformanceModeEnabled: Boolean,
        val artMainlineVersion: Long,
        val osCodenameAbbreviated: String,
        val compilationMode: String
    )

    @Serializable
    data class Build(
        val brand: String,
        val device: String,
        val fingerprint: String,
        val id: String,
        val model: String,
        val type: String,
        val version: Version
    )

    @Serializable
    data class Version(
        val codename: String,
        val sdk: Int
    )

    @Serializable
    data class Benchmark(
        val name: String,
        val params: Map<String, String>,
        val className: String,
        val totalRunTimeNs: Long,
        val metrics: Map<String, Metric>,
        val sampledMetrics: Map<String, SampledMetric>,
        val warmupIterations: Int,
        val repeatIterations: Int,
        val thermalThrottleSleepSeconds: Int,
        val profilerOutputs: List<ProfilerOutput>
    )

    @Serializable
    data class Metric(
        val minimum: Double,
        val maximum: Double,
        val median: Double,
        val runs: List<Double>
    )

    @Serializable
    data class SampledMetric(
        val P50: Double,
        val P90: Double,
        val P95: Double,
        val P99: Double,
        val runs: List<List<Double>>
    )

    @Serializable
    data class ProfilerOutput(
        val type: String,
        val label: String,
        val filename: String
    )
}

