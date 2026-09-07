/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfilesImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.ScheduledExecutorService

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PendingTriggerProfilesTest {

    private val now = 10_000L
    private val timeoutMs = PendingTriggerProfiles.EXPIRY_TIMEOUT_MS

    @Mock
    private lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @TempDir
    private lateinit var tempDir: File

    private fun perfettoResult(
        detectedAtMs: Long,
        path: String,
        startReason: ProfilingStartReason = ProfilingStartReason.ANR
    ): PerfettoResult = PerfettoResult(
        start = detectedAtMs,
        startReason = startReason,
        end = detectedAtMs,
        resultFilePath = path
    )

    private fun anrErrorEvent(
        timestamp: Long,
        rumErrorId: String = "err-1"
    ): ProfilerEvent.RumAnrEvent = ProfilerEvent.RumAnrEvent(
        id = rumErrorId,
        startMs = timestamp,
        durationNs = 1_000L,
        rumContext = ProfilingRumContext("app", "sess", null, null)
    )

    private fun fixedTimeProvider(time: Long = now): TimeProvider = object : TimeProvider {
        override fun getDeviceTimestampMillis(): Long = time
        override fun getServerTimestampMillis(): Long = time
        override fun getDeviceElapsedTimeNanos(): Long = time * 1_000_000L
        override fun getServerOffsetNanos(): Long = 0L
        override fun getServerOffsetMillis(): Long = 0L
        override fun getDeviceElapsedRealtimeMillis(): Long = time
        override fun getDeviceElapsedRealtimeNanos(): Long = time * 1_000_000L
        override fun getDeviceUptimeMillis(): Long = time
    }

    private fun testedBuffer(
        onMatch: (PerfettoResult, ProfilerEvent) -> Unit = { _, _ -> }
    ): PendingTriggerProfilesImpl = PendingTriggerProfilesImpl(
        executor = mockExecutor,
        timeProvider = fixedTimeProvider(),
        internalLogger = mockInternalLogger,
        onMatch = onMatch
    )

    @Test
    fun `M invoke onMatch W addRumGatingEvent {profiling result then ANR signal}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        val path = "/tmp/anr.proto"
        buffer.addProfilingResult(perfettoResult(now, path))
        buffer.addRumGatingEvent(anrErrorEvent(now + 500L))

        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumAnrEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M invoke onMatch W addProfilingResult {ANR signal then profiling result}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addRumGatingEvent(anrErrorEvent(now))

        val path = "/tmp/anr.proto"
        buffer.addProfilingResult(perfettoResult(now + 500L, path))

        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumAnrEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M not invoke onMatch W addRumGatingEvent {no profiling result}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addRumGatingEvent(anrErrorEvent(now))

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W addProfilingResult {no signal}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addProfilingResult(perfettoResult(now, "/tmp/anr.proto"))

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W addRumGatingEvent {trigger type mismatch}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addProfilingResult(
            perfettoResult(now, "/tmp/anr.proto", startReason = ProfilingStartReason.CONTINUOUS)
        )
        // ANR signal vs CONTINUOUS profiling result — trigger types disagree
        buffer.addRumGatingEvent(anrErrorEvent(now + 500L))

        // nothing matches, and both sides remain pending
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M reject non-ANR gating event W addRumGatingEvent {RumLongTaskEvent}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addProfilingResult(perfettoResult(now, "/tmp/anr.proto"))
        // RumLongTaskEvent is not a valid gating event — silently rejected
        buffer.addRumGatingEvent(
            ProfilerEvent.RumLongTaskEvent(
                id = "lt-1",
                startMs = now,
                durationNs = 1_000L,
                rumContext = ProfilingRumContext("app", "sess", null, null)
            )
        )

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M return expired profiling result W sweep {past timeout}`() {
        val buffer = testedBuffer()
        val path = "/tmp/anr.proto"
        buffer.addProfilingResult(perfettoResult(now, path))
        val expired = buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        assertThat(expired?.resultFilePath).isEqualTo(path)
        // profiling result is gone after sweep — a later signal finds nothing to match
        buffer.addRumGatingEvent(anrErrorEvent(now + timeoutMs + 2L))
    }

    @Test
    fun `M drop expired signal W sweep {past timeout}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addRumGatingEvent(anrErrorEvent(now))
        buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        // signal expired — a later profiling result won't match
        buffer.addProfilingResult(perfettoResult(now + timeoutMs + 2L, "/tmp/x"))
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M drop gating event per server time W sweep {clock drift, device time says not expired}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        // signal timestamp is already server-time-adjusted
        buffer.addRumGatingEvent(anrErrorEvent(5_000L))

        // Device clock lags the server clock: comparing against device time would wrongly say
        // the signal is not expired yet. The signal must expire per server time.
        buffer.sweep(deviceNow = 9_000L, serverNow = 5_000L + timeoutMs)

        buffer.addProfilingResult(perfettoResult(9_100L, "/tmp/x"))
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M keep gating event per server time W sweep {clock drift, device time says expired}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addRumGatingEvent(anrErrorEvent(5_000L))

        // Device clock leads the server clock: comparing against device time would wrongly
        // expire the signal early. Server time says it is not expired yet.
        buffer.sweep(deviceNow = 5_000L + timeoutMs, serverNow = 5_000L + timeoutMs - 1L)

        buffer.addProfilingResult(perfettoResult(5_000L + timeoutMs, "/tmp/x"))
        assertThat(matched).hasSize(1)
    }

    @Test
    fun `M return held profiling result W clear`() {
        val buffer = testedBuffer()
        buffer.addProfilingResult(perfettoResult(now, "/a"))
        val result = buffer.clear()
        assertThat(result?.resultFilePath).isEqualTo("/a")
    }

    @Test
    fun `M delete old profiling result file in place W addProfilingResult {second result}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = PendingTriggerProfilesImpl(
            executor = mockExecutor,
            timeProvider = fixedTimeProvider(),
            internalLogger = mockInternalLogger,
            onMatch = { c, s -> matched.add(c to s) }
        )
        val oldFile = File(tempDir, "old.proto").apply { writeText("trace") }
        val newFile = File(tempDir, "new.proto").apply { writeText("trace") }
        buffer.addProfilingResult(perfettoResult(now, oldFile.absolutePath))
        buffer.addProfilingResult(perfettoResult(now + 100L, newFile.absolutePath))

        // the old profiling result file is deleted in place; the new one is the only one tracked
        assertThat(oldFile.exists()).isFalse
        buffer.addRumGatingEvent(anrErrorEvent(now + 100L))
        assertThat(matched.first().first.resultFilePath).isEqualTo(newFile.absolutePath)
    }
}
