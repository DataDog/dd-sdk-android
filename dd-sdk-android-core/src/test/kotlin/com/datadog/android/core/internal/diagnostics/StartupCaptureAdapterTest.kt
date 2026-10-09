/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.diagnostics

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.profiler.GlobalBenchmark
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import com.datadog.android.core.internal.diagnostics.FlagsStartupCapture as CaptureAdapter

internal class StartupCaptureAdapterTest {
    private val previous = GlobalBenchmark.getProfiler()

    @AfterEach
    fun tearDown() { GlobalBenchmark.register(previous) }

    @Test
    fun `M send diagnostic records only to USER W capture`() {
        // Given
        val logger = mock<InternalLogger>()
        val capture = CaptureAdapter(logger = logger, clock = { 10L })
        // When
        capture.getTracer("flags-startup").spanBuilder("test", emptyMap()).startSpan()
        // Then
        val message = argumentCaptor<() -> String>()
        verify(
            logger
        ).log(
            eq(InternalLogger.Level.INFO),
            eq(InternalLogger.Target.USER),
            message.capture(),
            eq(null),
            eq(false),
            eq(null)
        )
        assertThat(message.firstValue()).startsWith("DDFlagsStartup ").contains("test")
    }

    @Test
    fun `M correlate nested spans and stop at record cap W capture`() {
        val lines = mutableListOf<String>()
        val capture = CaptureAdapter(logger = mock(), maxRecords = 4, clock = { 10L }, sink = { lines.add(it) })
        GlobalBenchmark.register(capture)
        StartupTrace.span("outer") { StartupTrace.event("inner") }
        StartupTrace.event("over_limit")
        assertThat(GlobalBenchmark.getProfiler()).isSameAs(previous)
        val records = lines.map(::JSONObject)
        assertThat(
            records.map {
                it.getString("phase")
            }
        ).containsExactly("begin", "begin", "end", "end", "capture_stopped")
        assertThat(records[1].getLong("parent")).isEqualTo(records[0].getLong("id"))
        assertThat(records[0].getLong("ns")).isEqualTo(10L)
    }

    @Test
    fun `M bound capture by monotonic time W duration expires`() {
        var time = 10L
        val lines = mutableListOf<String>()
        GlobalBenchmark.register(
            CaptureAdapter(logger = mock(), durationNs = 5, clock = {
                time
            }, sink = { lines.add(it) })
        )
        StartupTrace.event("before")
        time = 16L
        StartupTrace.event("after")
        assertThat(GlobalBenchmark.getProfiler()).isSameAs(previous)
        assertThat(lines.map(::JSONObject).last().getString("phase")).isEqualTo("capture_stopped")
    }

    @Test
    fun `M preserve result and parent cleanup W sink throws`() {
        GlobalBenchmark.register(CaptureAdapter(logger = mock(), sink = { error("local sink failure") }))
        assertThat(StartupTrace.span("outer") { StartupTrace.span("inner") { 42 } }).isEqualTo(42)
    }
}
