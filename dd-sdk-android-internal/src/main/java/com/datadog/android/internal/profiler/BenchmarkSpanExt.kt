/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiler

/**
 * Wraps the provided lambda within a [BenchmarkSpan].
 * @param T the type returned by the lambda
 * @param operationName the name of the [BenchmarkSpan] created around the lambda
 * (default is `true`)
 * @param block the lambda function traced by this newly created [BenchmarkSpan]
 *
 */
inline fun <T : Any?> withinBenchmarkSpan(
    operationName: String,
    block: BenchmarkSpan.() -> T
): T {
    val tracer = GlobalBenchmark.get().getTracer("dd-sdk-android")

    val spanBuilder = tracer.spanBuilder(operationName)

    val span = spanBuilder.startSpan()

    return try {
        span.block()
    } finally {
        span.stop()
    }
}
