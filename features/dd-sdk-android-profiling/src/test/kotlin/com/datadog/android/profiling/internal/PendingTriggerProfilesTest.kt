/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PendingTriggerProfilesTest {

    private val now = 10_000L
    private val timeoutMs = PendingTriggerProfiles.EXPIRY_TIMEOUT_MS

    @Mock
    private lateinit var mockExecutor: ScheduledExecutorService

    private fun perfettoResult(
        detectedAtMs: Long,
        path: String,
        startReason: ProfilingStartReason = ProfilingStartReason.OUT_OF_MEMORY
    ): PerfettoResult = PerfettoResult(
        start = detectedAtMs,
        startReason = startReason,
        end = detectedAtMs,
        resultFilePath = path
    )

    private fun oomErrorEvent(
        timestamp: Long,
        rumErrorId: String = "err-1"
    ): ProfilerEvent.RumOomErrorEvent = ProfilerEvent.RumOomErrorEvent(
        id = rumErrorId,
        timestamp = timestamp,
        rumContext = ProfilingRumContext("app", "sess", null, null)
    )

    private fun anomalyErrorEvent(
        timestamp: Long,
        rumErrorId: String = "err-1"
    ): ProfilerEvent.RumAnomalyErrorEvent = ProfilerEvent.RumAnomalyErrorEvent(
        id = rumErrorId,
        timestamp = timestamp,
        rumContext = ProfilingRumContext("app", "sess", null, null)
    )

    private fun fixedTimeProvider(time: Long = now): TimeProvider = object : TimeProvider {
        override fun getDeviceTimestampMillis(): Long = time
        override fun getServerTimestampMillis(): Long = time
        override fun getDeviceElapsedTimeNanos(): Long = time * 1_000_000L
        override fun getServerOffsetNanos(): Long = 0L
        override fun getServerOffsetMillis(): Long = 0L
        override fun getDeviceElapsedRealtimeMillis(): Long = time
        override fun getDeviceUptimeMillis(): Long = time
    }

    private fun testedBuffer(
        onMatch: (PerfettoResult, ProfilerEvent) -> Unit = { _, _ -> }
    ): PendingTriggerProfiles = PendingTriggerProfiles(
        executor = mockExecutor,
        timeProvider = fixedTimeProvider(),
        onMatch = onMatch
    )

    @Test
    fun `M invoke onMatch W addGatingSignal {capture then OOM signal}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        val path = "/tmp/hist.proto"
        buffer.addCapture(perfettoResult(now, path))
        buffer.addGatingSignal(oomErrorEvent(now + 500L))

        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumOomErrorEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M invoke onMatch W addCapture {anomaly signal then capture}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addGatingSignal(anomalyErrorEvent(now))

        val path = "/tmp/hist.proto"
        buffer.addCapture(
            perfettoResult(now + 500L, path, startReason = ProfilingStartReason.MEMORY_ANOMALY)
        )

        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumAnomalyErrorEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M not invoke onMatch W addGatingSignal {no capture}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addGatingSignal(oomErrorEvent(now))

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W addCapture {no signal}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addCapture(perfettoResult(now, "/tmp/hist.proto"))

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W addGatingSignal {trigger type mismatch}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addCapture(perfettoResult(now, "/tmp/hist.proto", startReason = ProfilingStartReason.OUT_OF_MEMORY))
        // ANOMALY signal vs OOM capture — trigger types disagree
        buffer.addGatingSignal(anomalyErrorEvent(now + 500L))

        // nothing matches, and both sides remain pending
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M reject non-OOM non-Anomaly gating signal W addGatingSignal {RumAnrEvent}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addCapture(perfettoResult(now, "/tmp/hist.proto"))
        // RumAnrEvent is not a valid gating signal — silently rejected
        buffer.addGatingSignal(
            ProfilerEvent.RumAnrEvent(
                id = "err-anr",
                startMs = now,
                durationNs = 1_000L,
                rumContext = ProfilingRumContext("app", "sess", null, null)
            )
        )

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M return expired capture W sweep {past timeout}`() {
        val expiredResults = mutableListOf<PerfettoResult>()
        val buffer = PendingTriggerProfiles(
            executor = mockExecutor,
            timeProvider = fixedTimeProvider(),
            onMatch = { _, _ -> },
            onExpired = expiredResults::add
        )
        val path = "/tmp/hist.proto"
        buffer.addCapture(perfettoResult(now, path))
        val expired = buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        assertThat(expired?.resultFilePath).isEqualTo(path)
        // capture is gone after sweep — a later signal finds nothing to match
        buffer.addGatingSignal(oomErrorEvent(now + timeoutMs + 2L))
    }

    @Test
    fun `M drop expired signal W sweep {past timeout}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addGatingSignal(oomErrorEvent(now))
        buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        // signal expired — a later capture won't match
        buffer.addCapture(perfettoResult(now + timeoutMs + 2L, "/tmp/x"))
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M drop gating signal per server time W sweep {clock drift, device time says not expired}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        // signal timestamp is already server-time-adjusted
        buffer.addGatingSignal(oomErrorEvent(5_000L))

        // Device clock lags the server clock: comparing against device time would wrongly say
        // the signal is not expired yet. The signal must expire per server time.
        buffer.sweep(deviceNow = 9_000L, serverNow = 5_000L + timeoutMs)

        buffer.addCapture(perfettoResult(9_100L, "/tmp/x"))
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M keep gating signal per server time W sweep {clock drift, device time says expired}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.addGatingSignal(oomErrorEvent(5_000L))

        // Device clock leads the server clock: comparing against device time would wrongly
        // expire the signal early. Server time says it is not expired yet.
        buffer.sweep(deviceNow = 5_000L + timeoutMs, serverNow = 5_000L + timeoutMs - 1L)

        buffer.addCapture(perfettoResult(5_000L + timeoutMs, "/tmp/x"))
        assertThat(matched).hasSize(1)
    }

    @Test
    fun `M return held capture W clear`() {
        val buffer = testedBuffer()
        buffer.addCapture(perfettoResult(now, "/a"))
        val result = buffer.clear()
        assertThat(result?.resultFilePath).isEqualTo("/a")
    }

    @Test
    fun `M override old capture and delete its file W addCapture {second capture}`() {
        val expiredResults = mutableListOf<PerfettoResult>()
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = PendingTriggerProfiles(
            executor = mockExecutor,
            timeProvider = fixedTimeProvider(),
            onMatch = { c, s -> matched.add(c to s) },
            onExpired = expiredResults::add
        )
        buffer.addCapture(perfettoResult(now, "/old"))
        buffer.addCapture(perfettoResult(now + 100L, "/new"))

        // the old capture is handed to onExpired; the new one is the only one tracked
        assertThat(expiredResults.map { it.resultFilePath }).containsExactly("/old")
        buffer.addGatingSignal(oomErrorEvent(now + 100L))
        assertThat(matched.first().first.resultFilePath).isEqualTo("/new")
    }

    @Test
    fun `M override capture across trigger types W addCapture {different startReason}`() {
        val expiredResults = mutableListOf<PerfettoResult>()
        val buffer = PendingTriggerProfiles(
            executor = mockExecutor,
            timeProvider = fixedTimeProvider(),
            onMatch = { _, _ -> },
            onExpired = expiredResults::add
        )
        buffer.addCapture(perfettoResult(now, "/oom").copy(startReason = ProfilingStartReason.OUT_OF_MEMORY))
        buffer.addCapture(
            perfettoResult(now + 100L, "/anomaly").copy(startReason = ProfilingStartReason.MEMORY_ANOMALY)
        )

        // the OOM capture is deleted; the anomaly capture is the only one tracked
        assertThat(expiredResults.map { it.resultFilePath }).containsExactly("/oom")
        assertThat(buffer.clear()?.resultFilePath).isEqualTo("/anomaly")
    }
}
