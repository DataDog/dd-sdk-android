/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.diagnostics

import android.os.Process
import android.util.Log
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.profiler.BenchmarkProfiler
import com.datadog.android.internal.profiler.BenchmarkSpan
import com.datadog.android.internal.profiler.BenchmarkSpanBuilder
import com.datadog.android.internal.profiler.BenchmarkTracer
import com.datadog.android.internal.profiler.GlobalBenchmark
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Bounded Logcat capture for this experimental diagnostic build. */
internal class FlagsStartupCapture(
    private val logger: InternalLogger,
    private val durationNs: Long = 60_000_000_000L,
    private val maxRecords: Long = 20_000L,
    private val clock: () -> Long = System::nanoTime,
    private val sink: (String) -> Unit = { message ->
        logger.log(InternalLogger.Level.INFO, InternalLogger.Target.USER, { "DDFlagsStartup $message" })
    }
) : BenchmarkProfiler {
    private val started = clock()
    private val previous = GlobalBenchmark.getProfiler()
    private val stopped = AtomicBoolean()
    private val sequence = AtomicLong()
    private val records = AtomicLong()
    private val parent = ThreadLocal<Long>()

    override fun getTracer(operation: String): BenchmarkTracer = object : BenchmarkTracer {
        override fun spanBuilder(spanName: String, additionalProperties: Map<String, String>): BenchmarkSpanBuilder =
            object : BenchmarkSpanBuilder {
                // Plain ThreadLocal has no throwing initializer or overridden accessors.
                @Suppress("UnsafeThirdPartyFunctionCall")
                override fun startSpan(): BenchmarkSpan {
                    val id = sequence.incrementAndGet()
                    val enclosing = parent.get()
                    parent.set(id)
                    emit("begin", spanName, id, enclosing, additionalProperties)
                    return object : BenchmarkSpan {
                        override fun stop() {
                            try {
                                emit("end", spanName, id, enclosing, emptyMap())
                            } finally {
                                if (enclosing == null) parent.remove() else parent.set(enclosing)
                            }
                        }
                    }
                }
            }
    }

    // JSON uses non-null literal keys; any sink/encoding failure is caught.
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun emit(phase: String, name: String, id: Long, enclosing: Long?, properties: Map<String, String>) {
        try {
            val now = clock()
            val count = records.incrementAndGet()
            if (now - started > durationNs || count > maxRecords) {
                if (stopped.compareAndSet(false, true) && GlobalBenchmark.getProfiler() === this) {
                    GlobalBenchmark.register(previous)
                    sink(
                        JSONObject(
                            mapOf(
                                "phase" to "capture_stopped",
                                "ns" to now,
                                "records" to count,
                                "pid" to Process.myPid()
                            )
                        ).toString()
                    )
                }
                return
            }
            sink(
                JSONObject(
                    mapOf(
                        "phase" to phase,
                        "name" to name,
                        "id" to id,
                        "parent" to (enclosing ?: 0L),
                        "ns" to now,
                        "pid" to Process.myPid(),
                        "thread" to Process.myTid(),
                        "properties" to properties
                    )
                ).toString()
            )
        } catch (_: Throwable) {
            // Capture failures must not affect the SDK; USER logging writes only to Logcat.
        }
    }

    companion object {
        const val TAG = "DDFlagsStartup"

        // The tag is non-null and shorter than Android's legacy 23-character limit.
        @Suppress("UnsafeThirdPartyFunctionCall")
        fun installIfEnabled(debuggable: Boolean, logger: InternalLogger) {
            if (debuggable && Log.isLoggable(TAG, Log.DEBUG) && GlobalBenchmark.getProfiler() !is FlagsStartupCapture) {
                GlobalBenchmark.register(FlagsStartupCapture(logger))
            }
        }
    }
}
