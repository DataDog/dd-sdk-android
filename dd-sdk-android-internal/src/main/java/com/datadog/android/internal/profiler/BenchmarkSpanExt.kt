/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

import androidx.tracing.Trace

/**
 * Wraps the provided lambda within a [BenchmarkSpan].
 * @param T the type returned by the lambda
 * @param operationName the name of the [BenchmarkSpan] created around the lambda
 * (default is `true`)
 * @param additionalProperties Additional properties for this span.
 * @param block the lambda function traced by this newly created [BenchmarkSpan]
 *
 */
inline fun <T> withinBenchmarkSpan(
    operationName: String,
    additionalProperties: Map<String, String> = emptyMap(),
    block: BenchmarkSpan.() -> T
): T {
    val tracer = GlobalBenchmark.getProfiler().getTracer("dd-sdk-android")

    val spanBuilder = tracer.spanBuilder(
        operationName,
        additionalProperties
    )

    val span = spanBuilder.startSpan()

    // Mirror the span as an androidx.tracing section so Jetpack Macrobenchmark's
    // TraceSectionMetric can measure these operations (e.g. "SnapshotProducer"). This is gated on a
    // registered benchmark profiler so it adds no overhead on this hot path in production.
    val traceSectionStarted = beginBenchmarkTraceSection(operationName)

    return try {
        span.block()
    } finally {
        if (traceSectionStarted) {
            endBenchmarkTraceSection()
        }
        span.stop()
    }
}

/**
 * Begins an androidx.tracing section named [operationName] when a benchmark profiler is registered.
 * Extracted as a non-inline function so the androidx.tracing dependency stays an implementation
 * detail of this module and is not leaked onto callers of the public inline [withinBenchmarkSpan].
 * @return `true` if a section was started and must be matched by [endBenchmarkTraceSection].
 */
@PublishedApi
internal fun beginBenchmarkTraceSection(operationName: String): Boolean {
    if (!GlobalBenchmark.isProfilerRegistered()) {
        return false
    }
    Trace.beginSection(operationName.take(MAX_TRACE_SECTION_NAME_LENGTH))
    return true
}

/**
 * Ends the most recently started androidx.tracing section opened by [beginBenchmarkTraceSection].
 */
@PublishedApi
internal fun endBenchmarkTraceSection() {
    Trace.endSection()
}

// android.os.Trace section names are limited to 127 characters.
@PublishedApi
internal const val MAX_TRACE_SECTION_NAME_LENGTH: Int = 127
