/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.internal.profiler.BenchmarkProfiler
import com.datadog.android.internal.profiler.BenchmarkSpan
import com.datadog.android.internal.profiler.BenchmarkSpanBuilder
import com.datadog.android.internal.profiler.BenchmarkTracer
import com.datadog.android.internal.profiler.GlobalBenchmark
import com.datadog.android.internal.thread.NamedRunnable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class StartupExecutorTraceTest {
    @Test
    fun `M identify task ahead of read without changing runnable W queued cache read`() {
        val previous = GlobalBenchmark.getProfiler()
        val capture = FlagsStartupCapture()
        GlobalBenchmark.register(capture)
        val executor = BackPressureExecutorService(
            mock(),
            "storage",
            BackPressureStrategy(10, {}, {}, BackPressureMitigation.IGNORE_NEWEST),
            mock()
        )
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        val read = NamedRunnable("dataStoreRead") { }
        try {
            executor.execute(
                NamedRunnable("Data migration") {
                    running.countDown()
                    release.await()
                }
            )
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue()
            executor.execute(read)
            assertThat(executor.queue.peek()).isSameAs(read)
            release.countDown()
            executor.shutdown()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
            val starts = capture.events.filter { it.first == "executor.start" }
            assertThat(starts.map { it.second["task_name"] }).containsExactly("data_migration", "datastoreread")
            val enqueued = capture.events.first {
                it.first == "executor.enqueue" &&
                    it.second["task_name"] == "datastoreread"
            }
            assertThat(starts.last().second["task"]).isEqualTo(enqueued.second["task"])
            assertThat(capture.events.count { it.first == "executor.end" }).isEqualTo(2)
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
            GlobalBenchmark.register(previous)
        }
    }
}

private class FlagsStartupCapture : BenchmarkProfiler {
    val events = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
    override fun getTracer(operation: String): BenchmarkTracer = object : BenchmarkTracer {
        override fun spanBuilder(spanName: String, additionalProperties: Map<String, String>): BenchmarkSpanBuilder =
            object : BenchmarkSpanBuilder {
                override fun startSpan(): BenchmarkSpan {
                    events.add(spanName to additionalProperties)
                    return object : BenchmarkSpan {
                        override fun stop() = Unit
                    }
                }
            }
    }
}
