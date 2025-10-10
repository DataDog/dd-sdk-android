/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Provides an interface to collect counters and gauges in the benchmark environment.
 * During benchmarking a concrete implementation is used, but in production it is a noop
 */
@NoOpImplementation
interface BenchmarkMeter {

    /**
     * Gets a [BenchmarkCounter] for the given counter name.
     *
     */
    fun getCounter(
        operation: String
    ): BenchmarkCounter

    /**
     * Creates an observable gauge for the parameters.
     *
     */
    fun createObservableGauge(
        metricName: String,
        tags: Map<String, String>,
        callback: () -> Double
    )
}
