/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.diagnostics

import com.datadog.android.internal.profiler.BenchmarkSpan
import com.datadog.android.internal.profiler.GlobalBenchmark
import java.util.concurrent.atomic.AtomicLong

// Diagnostic builds register a bounded USER Logcat capture during core initialization.
internal object StartupTrace {
    const val REPOSITORY = "repository"
    const val TRACKING = "resolution.tracking"
    const val CLIENT = "client"
    const val SDK = "sdk"
    const val CALLER = "caller"
    const val OPERATION = "operation"
    const val STATE_READ = "state.read"

    private val sequence = AtomicLong(1L)

    fun enabled(): Boolean = GlobalBenchmark.isProfilerEnabled()

    fun nextId(): Long = if (enabled()) sequence.getAndIncrement() else 0L

    fun id(value: Any?): String = if (value == null) "none" else System.identityHashCode(value).toString()

    @Suppress("TooGenericExceptionCaught") // A diagnostic adapter must never affect SDK operations.
    inline fun begin(name: String, properties: () -> Map<String, String> = { emptyMap() }): BenchmarkSpan? = try {
        if (enabled()) {
            GlobalBenchmark.getProfiler().getTracer("flags-startup")
                .spanBuilder(name, properties()).startSpan()
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    @Suppress("TooGenericExceptionCaught") // A failing local sink must not escape into SDK operations.
    fun end(span: BenchmarkSpan?) {
        try {
            span?.stop()
        } catch (_: Throwable) {
            // Diagnostic failures must not affect SDK operations.
        }
    }

    inline fun event(name: String, properties: () -> Map<String, String> = { emptyMap() }) {
        end(begin(name, properties))
    }

    inline fun <T> span(
        name: String,
        properties: () -> Map<String, String> = { emptyMap() },
        block: () -> T
    ): T {
        val span = begin(name, properties)
        var completed = false
        try {
            return block().also { completed = true }
        } finally {
            if (!completed) event("operation.threw")
            end(span)
        }
    }
}
