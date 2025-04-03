/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

/**
 * A global holder of [BenchmarkProfiler]
 * allowing registration and retrieval of [BenchmarkProfiler] and [BenchmarkMeter] implementations.
 * This should only used by internal benchmarking.
 */
object GlobalBenchmark {

    private var benchmarkProfiler: BenchmarkProfiler = NoOpBenchmarkProfiler()
    private var benchmarkSdkPerformance: BenchmarkSdkPerformance =
        NoOpBenchmarkSdkPerformance()

    /**
     * Registers the implementation of [BenchmarkProfiler].
     */
    fun register(benchmarkProfiler: BenchmarkProfiler) {
        this.benchmarkProfiler = benchmarkProfiler
    }

    /**
     * Registers the implementation of [BenchmarkSdkPerformance].
     */
    fun register(benchmarkSdkPerformance: BenchmarkSdkPerformance) {
        this.benchmarkSdkPerformance = benchmarkSdkPerformance
    }

    /**
     * Returns the [BenchmarkProfiler] registered.
     */
    fun getProfiler(): BenchmarkProfiler {
        return benchmarkProfiler
    }

    /**
     * Returns the [BenchmarkSdkPerformance] registered.
     */
    fun getSdkPerformance(): BenchmarkSdkPerformance {
        return benchmarkSdkPerformance
    }

    /**
     * Creates the appropriate [ExecutionTimer].
     */
    fun createExecutionTimer(): ExecutionTimer {
        if (benchmarkSdkPerformance is NoOpBenchmarkSdkPerformance) {
            return NoOpExecutionTimer()
        }

        return DDExecutionTimer(benchmarkSdkPerformance)
    }
}
