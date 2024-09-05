/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkSpan
import com.datadog.android.internal.profiler.BenchmarkSpanBuilder
import io.opentelemetry.api.trace.SpanBuilder

/**
 * Implementation of [BenchmarkSpanBuilder].
 */
class DDBenchmarkSpanBuilder(
    private val spanBuilder: SpanBuilder
) : BenchmarkSpanBuilder {

    override fun startSpan(): BenchmarkSpan {
        val span = spanBuilder.startSpan()
        return DDBenchmarkSpan(span, span.makeCurrent())
    }
}
