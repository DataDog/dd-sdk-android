/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.fixtures.ProfilingStorageFixture
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.perfetto.ProfileType
import com.datadog.android.profiling.internal.trigger.PendingOomGatingEvent
import com.datadog.android.profiling.internal.trigger.PendingOomProfile
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfileStorage
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.ScheduledExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PendingTriggerProfilesTest {

    private val now = 10_000L
    private val timeoutMs = PendingTriggerProfiles.EXPIRY_TIMEOUT_MS

    @Mock
    private lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferencesStorage: SharedPreferencesStorage

    @TempDir
    private lateinit var tempDir: File

    @LongForgery
    private var fakeBootNtpOffsetNs: Long = 0L

    private val fakeQuotaDeniedSessionIds = mutableSetOf<String>()

    @BeforeEach
    fun setUp() {
        ProfilingStorageFixture.stubWith(mockSharedPreferencesStorage)
    }

    @AfterEach
    fun tearDown() {
        ProfilingStorageFixture.reset()
    }

    // region ANR (deletes the result file in place on expiry)

    @Test
    fun `M invoke onMatch W setRumGatingEvent {profiling result then ANR signal}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        val path = "/tmp/anr.proto"
        buffer.setProfilingResult(perfettoResult(now, path))
        buffer.setRumGatingEvent(anrErrorEvent(now + 500L))

        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumAnrEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M invoke onMatch W setProfilingResult {ANR signal then profiling result}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setRumGatingEvent(anrErrorEvent(now))

        val path = "/tmp/anr.proto"
        buffer.setProfilingResult(perfettoResult(now + 500L, path))

        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumAnrEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M not invoke onMatch W setRumGatingEvent {no profiling result}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setRumGatingEvent(anrErrorEvent(now))

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W setProfilingResult {no signal}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setProfilingResult(perfettoResult(now, "/tmp/anr.proto"))

        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W setRumGatingEvent {trigger type mismatch}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setProfilingResult(
            perfettoResult(now, "/tmp/anr.proto", startReason = ProfilingStartReason.CONTINUOUS)
        )
        // ANR signal vs CONTINUOUS profiling result — trigger types disagree
        buffer.setRumGatingEvent(anrErrorEvent(now + 500L))

        // nothing matches, and both sides remain pending
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M reject non-ANR gating event W setRumGatingEvent {RumLongTaskEvent}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setProfilingResult(perfettoResult(now, "/tmp/anr.proto"))
        // RumLongTaskEvent is not a valid gating event — silently rejected
        buffer.setRumGatingEvent(
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
        buffer.setProfilingResult(perfettoResult(now, path))
        val expired = buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        assertThat(expired?.resultFilePath).isEqualTo(path)
        // profiling result is gone after sweep — a later signal finds nothing to match
        buffer.setRumGatingEvent(anrErrorEvent(now + timeoutMs + 2L))
    }

    @Test
    fun `M drop expired signal W sweep {past timeout}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setRumGatingEvent(anrErrorEvent(now))
        buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        // signal expired — a later profiling result won't match
        buffer.setProfilingResult(perfettoResult(now + timeoutMs + 2L, "/tmp/x"))
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M drop gating event per server time W sweep {clock drift, device time says not expired}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        // signal timestamp is already server-time-adjusted
        buffer.setRumGatingEvent(anrErrorEvent(5_000L))

        // Device clock lags the server clock: comparing against device time would wrongly say
        // the signal is not expired yet. The signal must expire per server time.
        buffer.sweep(deviceNow = 9_000L, serverNow = 5_000L + timeoutMs)

        buffer.setProfilingResult(perfettoResult(9_100L, "/tmp/x"))
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M keep gating event per server time W sweep {clock drift, device time says expired}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setRumGatingEvent(anrErrorEvent(5_000L))

        // Device clock leads the server clock: comparing against device time would wrongly
        // expire the signal early. Server time says it is not expired yet.
        buffer.sweep(deviceNow = 5_000L + timeoutMs, serverNow = 5_000L + timeoutMs - 1L)

        buffer.setProfilingResult(perfettoResult(5_000L + timeoutMs, "/tmp/x"))
        assertThat(matched).hasSize(1)
    }

    @Test
    fun `M return held profiling result W clear`() {
        val buffer = testedBuffer()
        buffer.setProfilingResult(perfettoResult(now, "/a"))
        val result = buffer.clear()
        assertThat(result?.resultFilePath).isEqualTo("/a")
    }

    @Test
    fun `M delete old profiling result file in place W setProfilingResult {second result}`() {
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = PendingTriggerProfileStorage(
            appContext = mockContext,
            executor = mockExecutor,
            timeProvider = fixedTimeProvider(),
            internalLogger = mockInternalLogger,
            callback = stubCallback(onMatch = { c, s -> matched.add(c to s) })
        )
        val oldFile = File(tempDir, "old.proto").apply { writeText("trace") }
        val newFile = File(tempDir, "new.proto").apply { writeText("trace") }
        buffer.setProfilingResult(perfettoResult(now, oldFile.absolutePath))
        buffer.setProfilingResult(perfettoResult(now + 100L, newFile.absolutePath))

        // the old profiling result file is deleted in place; the new one is the only one tracked
        assertThat(oldFile.exists()).isFalse
        buffer.setRumGatingEvent(anrErrorEvent(now + 100L))
        assertThat(matched.first().first.resultFilePath).isEqualTo(newFile.absolutePath)
    }

    // endregion

    // region OOM (same buffer as ANR: deletes the result file in place)

    @Test
    fun `M invoke onMatch W setRumGatingEvent {profiling result then OOM signal}`() {
        // Given
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedHeapHistogramBuffer(onMatch = { c, s -> matched.add(c to s) })
        val path = "/tmp/hist.proto"
        buffer.setProfilingResult(perfettoResult(now, path, startReason = ProfilingStartReason.OUT_OF_MEMORY))

        // When
        buffer.setRumGatingEvent(oomErrorEvent(now + 500L))

        // Then
        assertThat(matched).hasSize(1)
        assertThat(matched.first().first.resultFilePath).isEqualTo(path)
        assertThat((matched.first().second as ProfilerEvent.RumOomErrorEvent).id).isEqualTo("err-1")
    }

    @Test
    fun `M not invoke onMatch W setRumGatingEvent {no profiling result, OOM}`() {
        // Given
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedHeapHistogramBuffer(onMatch = { c, s -> matched.add(c to s) })

        // When
        buffer.setRumGatingEvent(oomErrorEvent(now))

        // Then
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M not invoke onMatch W setProfilingResult {no signal, OOM}`() {
        // Given
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedHeapHistogramBuffer(onMatch = { c, s -> matched.add(c to s) })

        // When
        buffer.setProfilingResult(
            perfettoResult(now, "/tmp/hist.proto", startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // Then
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M reject non-OOM gating event W setRumGatingEvent {RumAnrEvent}`() {
        // Given
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedHeapHistogramBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setProfilingResult(
            perfettoResult(now, "/tmp/hist.proto", startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // When — RumAnrEvent is not a valid gating event for the heap histogram buffer
        buffer.setRumGatingEvent(anrErrorEvent(now, rumErrorId = "err-anr"))

        // Then — silently rejected
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M return expired profiling result W sweep {past timeout, OOM}`() {
        // Given
        val buffer = testedHeapHistogramBuffer()
        val path = "/tmp/hist.proto"
        buffer.setProfilingResult(perfettoResult(now, path, startReason = ProfilingStartReason.OUT_OF_MEMORY))

        // When
        val expired = buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        // Then
        assertThat(expired?.resultFilePath).isEqualTo(path)
        // profiling result is gone after sweep — a later signal finds nothing to match
        buffer.setRumGatingEvent(oomErrorEvent(now + timeoutMs + 2L))
    }

    @Test
    fun `M drop expired signal W sweep {past timeout, OOM}`() {
        // Given
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedHeapHistogramBuffer(onMatch = { c, s -> matched.add(c to s) })
        buffer.setRumGatingEvent(oomErrorEvent(now))
        buffer.sweep(now + timeoutMs + 1L, now + timeoutMs + 1L)

        // When — signal expired, so a later profiling result won't match
        buffer.setProfilingResult(
            perfettoResult(now + timeoutMs + 2L, "/tmp/x", startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // Then
        assertThat(matched).isEmpty()
    }

    @Test
    fun `M return held profiling result W clear {OOM}`() {
        // Given
        val buffer = testedHeapHistogramBuffer()
        buffer.setProfilingResult(perfettoResult(now, "/a", startReason = ProfilingStartReason.OUT_OF_MEMORY))

        // When
        val result = buffer.clear()

        // Then
        assertThat(result?.resultFilePath).isEqualTo("/a")
    }

    @Test
    fun `M persist with the gating event boot offset W setProfilingResult {deferred OOM gating event, within max age}`(
        @Forgery fakeGatingEvent: PendingOomGatingEvent,
        @StringForgery fakePath: String,
        @LongForgery(min = 1_000L, max = 1_000_000L) fakeNowMs: Long
    ) {
        // Given
        // The result may be delivered after a reboot: its offset comes from the current boot,
        // while the histogram was captured in the boot the gating event was received in.
        val deferredGatingEvent = fakeGatingEvent.copy(
            timestampMs = fakeNowMs,
            bootNtpOffsetNs = fakeBootNtpOffsetNs + 1L
        )
        whenever(
            mockSharedPreferencesStorage.getString(
                eq(ProfilingStorage.KEY_PENDING_OOM_GATING_EVENT),
                anyOrNull()
            )
        ) doReturn deferredGatingEvent.toJson()
        var deferredReadyCount = 0
        val buffer = PendingTriggerProfileStorage(
            appContext = mockContext,
            executor = mockExecutor,
            timeProvider = fixedTimeProvider(fakeNowMs),
            internalLogger = mockInternalLogger,
            callback = stubCallback(onDeferredOomProfileReady = { deferredReadyCount++ })
        )

        // When
        buffer.setProfilingResult(
            perfettoResult(fakeNowMs, fakePath, startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // Then
        val pendingProfileCaptor = argumentCaptor<String>()
        verify(mockSharedPreferencesStorage).putString(
            eq(ProfilingStorage.KEY_PENDING_OOM_PROFILE),
            pendingProfileCaptor.capture(),
            eq(true)
        )
        val pendingProfile = checkNotNull(PendingOomProfile.fromJson(pendingProfileCaptor.firstValue))
        assertThat(pendingProfile.bootNtpOffsetNs).isEqualTo(deferredGatingEvent.bootNtpOffsetNs)
        assertThat(deferredReadyCount).isEqualTo(1)
        assertThat(buffer.clear()).isNull()
    }

    @Test
    fun `M not persist the gating event W setRumGatingEvent {OOM, quota denied}`(
        @Forgery fakeOomEvent: ProfilerEvent.RumOomErrorEvent
    ) {
        // Given
        fakeQuotaDeniedSessionIds.add(fakeOomEvent.rumContext.sessionId)
        val buffer = testedHeapHistogramBuffer()

        // When
        buffer.setRumGatingEvent(fakeOomEvent)

        // Then
        verify(mockSharedPreferencesStorage, never()).putString(
            eq(ProfilingStorage.KEY_PENDING_OOM_GATING_EVENT),
            any(),
            any()
        )
    }

    @Test
    fun `M drop the profile W setProfilingResult {OOM matched in-process, quota denied after gating event}`(
        @Forgery fakeOomEvent: ProfilerEvent.RumOomErrorEvent,
        @LongForgery(min = 1_000L, max = 1_000_000L) fakeDetectedAtMs: Long,
        @StringForgery fakeFileName: String,
        @StringForgery fakeTraceContent: String
    ) {
        // Given
        val traceFile = File(tempDir, fakeFileName).apply { writeText(fakeTraceContent) }
        val buffer = testedHeapHistogramBuffer()
        buffer.setRumGatingEvent(fakeOomEvent)
        fakeQuotaDeniedSessionIds.add(fakeOomEvent.rumContext.sessionId)

        // When
        buffer.setProfilingResult(
            perfettoResult(fakeDetectedAtMs, traceFile.absolutePath, startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // Then
        verify(mockSharedPreferencesStorage, never()).putString(
            eq(ProfilingStorage.KEY_PENDING_OOM_PROFILE),
            any(),
            any()
        )
        assertThat(traceFile.exists()).isFalse
        verify(mockSharedPreferencesStorage).remove(ProfilingStorage.KEY_PENDING_OOM_GATING_EVENT, true)
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.DEBUG,
            target = InternalLogger.Target.USER,
            message = PendingTriggerProfileStorage.LOG_OOM_PROFILE_DROPPED_QUOTA_DENIED
        )
    }

    @Test
    fun `M delete old profiling result file in place W setProfilingResult {second result, OOM}`() {
        // Given
        val matched = mutableListOf<Pair<PerfettoResult, ProfilerEvent>>()
        val buffer = testedHeapHistogramBuffer(onMatch = { c, s -> matched.add(c to s) })
        val oldFile = File(tempDir, "old.proto").apply { writeText("trace") }
        val newFile = File(tempDir, "new.proto").apply { writeText("trace") }
        buffer.setProfilingResult(
            perfettoResult(now, oldFile.absolutePath, startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // When
        buffer.setProfilingResult(
            perfettoResult(now + 100L, newFile.absolutePath, startReason = ProfilingStartReason.OUT_OF_MEMORY)
        )

        // Then — the old profiling result file is deleted in place; the new one is the only one tracked
        assertThat(oldFile.exists()).isFalse
        buffer.setRumGatingEvent(oomErrorEvent(now + 100L))
        assertThat(matched.first().first.resultFilePath).isEqualTo(newFile.absolutePath)
    }

    // endregion

    // region Tests fixtures

    private fun perfettoResult(
        detectedAtMs: Long,
        path: String,
        startReason: ProfilingStartReason = ProfilingStartReason.ANR
    ): PerfettoResult = PerfettoResult(
        start = detectedAtMs,
        startReason = startReason,
        end = detectedAtMs,
        resultFilePath = path,
        profileTypes = listOf(
            if (startReason == ProfilingStartReason.ANR) {
                ProfileType.SYSTEM_TRACE
            } else {
                ProfileType.STACK_SAMPLING
            }
        ),
        bootNtpOffsetNs = fakeBootNtpOffsetNs
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

    private fun oomErrorEvent(
        timestamp: Long,
        rumErrorId: String = "err-1"
    ): ProfilerEvent.RumOomErrorEvent = ProfilerEvent.RumOomErrorEvent(
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
        override fun getDeviceElapsedRealtimeNanos(): Long = time * 1_000_000L
        override fun getDeviceUptimeMillis(): Long = time
    }

    private fun testedBuffer(
        onMatch: (PerfettoResult, ProfilerEvent) -> Unit = { _, _ -> }
    ): PendingTriggerProfileStorage = PendingTriggerProfileStorage(
        appContext = mockContext,
        executor = mockExecutor,
        timeProvider = fixedTimeProvider(),
        internalLogger = mockInternalLogger,
        callback = stubCallback(onMatch = onMatch)
    )

    private fun testedHeapHistogramBuffer(
        onMatch: (PerfettoResult, ProfilerEvent) -> Unit = { _, _ -> }
    ): PendingTriggerProfileStorage = PendingTriggerProfileStorage(
        appContext = mockContext,
        executor = mockExecutor,
        timeProvider = fixedTimeProvider(),
        internalLogger = mockInternalLogger,
        callback = stubCallback(onMatch = onMatch)
    )

    private fun stubCallback(
        onMatch: (PerfettoResult, ProfilerEvent) -> Unit = { _, _ -> },
        onDeferredOomProfileReady: () -> Unit = {}
    ) = object : PendingTriggerProfileStorage.Callback {
        override fun onMatch(result: PerfettoResult, event: ProfilerEvent) = onMatch(result, event)

        override fun onDeferredOomProfileReady() = onDeferredOomProfileReady()

        override fun isSessionQuotaDenied(sessionId: String) = fakeQuotaDeniedSessionIds.contains(sessionId)
    }

    // endregion
}
