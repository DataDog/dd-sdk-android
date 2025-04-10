/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

internal class DDExecutionTimer(
    private val track: String,
    private val benchmarkSdkUploads: BenchmarkSdkUploads = GlobalBenchmark.getBenchmarkSdkUploads()
) : ExecutionTimer {
    override fun <T> measure(action: () -> T): T {
        if (track.isEmpty()) {
            return action()
        }

        val requestStartTime = System.nanoTime()
        val result = action()
        val latencyInSeconds = (System.nanoTime() - requestStartTime) / NANOSECONDS_IN_A_SECOND
        responseLatencyReport(latencyInSeconds, track)
        return result
    }

    private fun responseLatencyReport(latencySeconds: Double, track: String) {
        val tags = mapOf(
            TRACK_NAME to track
        )

        benchmarkSdkUploads
            .getMeter(METER_NAME)
            .createObservableGauge(BENCHMARK_RESPONSE_LATENCY, tags) {
                latencySeconds
            }
    }

    private companion object {
        private const val TRACK_NAME = "track"
        private const val METER_NAME = "dd-sdk-android"
        private const val BENCHMARK_RESPONSE_LATENCY = "android.benchmark.response_latency"
        private const val NANOSECONDS_IN_A_SECOND = 1_000_000_000.0
    }
}
