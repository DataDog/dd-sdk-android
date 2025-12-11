/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

import com.datadog.android.internal.time.TimeProvider

/**
 * A global holder of [BenchmarkProfiler]
 * allowing registration and retrieval of [BenchmarkProfiler] and [BenchmarkMeter] implementations.
 * This should only used by internal benchmarking.
 */
object GlobalBenchmark {

    private var benchmarkProfiler: BenchmarkProfiler = NoOpBenchmarkProfiler()
    private var benchmarkSdkUploads: BenchmarkSdkUploads =
        NoOpBenchmarkSdkUploads()

    /**
     * Registers the implementation of [BenchmarkProfiler].
     */
    fun register(benchmarkProfiler: BenchmarkProfiler) {
        this.benchmarkProfiler = benchmarkProfiler
    }

    /**
     * Registers the implementation of [BenchmarkSdkUploads].
     */
    fun register(benchmarkSdkUploads: BenchmarkSdkUploads) {
        this.benchmarkSdkUploads = benchmarkSdkUploads
    }

    /**
     * Returns the [BenchmarkProfiler] registered.
     */
    fun getProfiler(): BenchmarkProfiler {
        return benchmarkProfiler
    }

    /**
     * Returns the [BenchmarkSdkUploads] registered.
     */
    fun getBenchmarkSdkUploads(): BenchmarkSdkUploads {
        return benchmarkSdkUploads
    }

    /**
     * Creates the appropriate [ExecutionTimer].
     */
    fun createExecutionTimer(track: String, timeProvider: TimeProvider): ExecutionTimer {
        if (benchmarkSdkUploads is NoOpBenchmarkSdkUploads) {
            return NoOpExecutionTimer()
        }

        return DDExecutionTimer(
            track = track,
            timeProvider = timeProvider
        )
    }
}
