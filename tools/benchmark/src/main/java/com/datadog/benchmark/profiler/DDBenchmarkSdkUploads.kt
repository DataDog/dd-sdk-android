/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkMeter
import com.datadog.android.internal.profiler.BenchmarkSdkUploads
import io.opentelemetry.api.GlobalOpenTelemetry

/**
 * Implementation of [BenchmarkSdkUploads] for internal benchmarking.
 */
class DDBenchmarkSdkUploads : BenchmarkSdkUploads {
    override fun getMeter(operation: String): BenchmarkMeter {
        return DDBenchmarkMeter(GlobalOpenTelemetry.get().getMeter(operation))
    }
}
