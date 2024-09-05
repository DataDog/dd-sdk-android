/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.profiler

import com.datadog.android.internal.profiler.BenchmarkSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope

/**
 * Implementation of [BenchmarkSpan].
 */
class DDBenchmarkSpan(private val span: Span, private val scope: Scope) : BenchmarkSpan {
    override fun stop() {
        scope.close()
        span.end()
    }
}
