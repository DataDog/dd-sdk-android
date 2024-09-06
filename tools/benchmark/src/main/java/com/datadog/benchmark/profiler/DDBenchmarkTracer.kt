/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkSpanBuilder
import com.datadog.android.internal.profiler.BenchmarkTracer
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

/**
 * Implementation of [BenchmarkTracer].
 */
class DDBenchmarkTracer(private val tracer: Tracer) : BenchmarkTracer {

    override fun spanBuilder(
        spanName: String,
        additionalProperties: Map<String, String>
    ): BenchmarkSpanBuilder {
        return DDBenchmarkSpanBuilder(
            tracer
                .spanBuilder(spanName)
                .apply {
                    additionalProperties.forEach {
                        this.setAttribute(it.key, it.value)
                    }
                }
                .setParent(Context.current())
        )
    }
}
