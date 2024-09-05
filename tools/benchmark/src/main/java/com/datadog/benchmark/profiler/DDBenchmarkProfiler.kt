/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkProfiler
import com.datadog.android.internal.profiler.BenchmarkTracer
import io.opentelemetry.api.GlobalOpenTelemetry

/**
 * Implementation of [BenchmarkProfiler].
 */
class DDBenchmarkProfiler : BenchmarkProfiler {

    override fun getTracer(operation: String): BenchmarkTracer {
        return DDBenchmarkTracer(GlobalOpenTelemetry.get().getTracer(operation))
    }
}
