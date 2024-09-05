/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

/**
 * A global holder of [BenchmarkProfiler], allowing to register and retrieve [BenchmarkProfiler] implementation.
 * This should only used by internal benchmarking.
 */
object GlobalBenchmark {

    private var benchmarkProfiler: BenchmarkProfiler = NoOpBenchmarkProfiler()

    /**
     * Registers the implementation of [BenchmarkProfiler].
     */
    fun register(benchmarkProfiler: BenchmarkProfiler) {
        GlobalBenchmark.benchmarkProfiler = benchmarkProfiler
    }

    /**
     * Returns the [BenchmarkProfiler] registered.
     */
    fun get(): BenchmarkProfiler {
        return benchmarkProfiler
    }
}
