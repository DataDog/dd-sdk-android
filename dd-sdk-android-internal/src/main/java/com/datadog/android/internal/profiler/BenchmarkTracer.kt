/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface of benchmark tracer to be implemented to provide [BenchmarkSpan].
 * This should only used by internal benchmarking.
 */
@NoOpImplementation
interface BenchmarkTracer {

    /**
     * Returns a new [BenchmarkSpanBuilder].
     *
     * @param spanName The name of the returned span.
     * @return a new [BenchmarkSpanBuilder].
     */
    fun spanBuilder(spanName: String): BenchmarkSpanBuilder
}
