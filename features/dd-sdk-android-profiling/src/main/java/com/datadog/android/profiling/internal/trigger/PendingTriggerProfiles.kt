/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilerEvent.RumAnomalyErrorEvent
import com.datadog.android.internal.profiling.ProfilerEvent.RumOomErrorEvent
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles.Companion.EXPIRY_TIMEOUT_MS
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Thread-safe buffer pairing a trigger-captured profiling artifact with the gating RUM error
 * event that promotes it to an upload.
 *
 * Holds at most one capture and one gating signal; a new capture overrides (and deletes the
 * file of) the previous one. Whichever arrives second completes the pair, but only when the
 * gating signal's trigger type matches the capture's [com.datadog.android.profiling.internal.perfetto.PerfettoResult.startReason] — a mismatch
 * stays pending for its true counterpart. Only [com.datadog.android.internal.profiling.ProfilerEvent.RumOomErrorEvent] and
 * [com.datadog.android.internal.profiling.ProfilerEvent.RumAnomalyErrorEvent] are accepted as gating signals.
 *
 * Each pending item schedules a one-shot cleanup after [EXPIRY_TIMEOUT_MS]; expired captures
 * are dropped and their file deleted via [onExpired] so nothing leaks. This is a pure pairing
 * structure — [com.datadog.android.profiling.internal.ProfilingFeature] owns the on-match dispatch.
 */
@Suppress("TooManyFunctions")
internal class PendingTriggerProfiles(
    private val executor: ScheduledExecutorService?,
    private val timeProvider: TimeProvider,
    private val onMatch: (PerfettoResult, ProfilerEvent) -> Unit,
    private val onExpired: ((PerfettoResult) -> Unit)? = null
) {
    private val lock = Any()
    private var capture: PerfettoResult? = null
    private var gatingEvent: ProfilerEvent? = null
    private var captureCleanupTask: ScheduledFuture<*>? = null
    private var signalCleanupTask: ScheduledFuture<*>? = null

    fun addCapture(result: PerfettoResult) {
        var overriddenCapture: PerfettoResult? = null
        val pair = synchronized(lock) {
            // At most one capture at a time; a new one overrides the old. Its file is deleted
            // below, via the onExpired callback, once the lock is released.
            overriddenCapture = capture
            capture = result
            matchLocked()
        }
        overriddenCapture?.let { onExpired?.invoke(it) }
        if (pair != null) {
            val (capture, gating) = pair
            onMatch(capture, gating)
        } else {
            // No match yet — self-clean this capture after the expiry timeout.
            scheduleCaptureCleanup()
        }
    }

    fun addGatingSignal(signal: ProfilerEvent) {
        // Only OOM and Anomaly error events are valid gating signals.
        if (signal.triggerType() == null) return
        val pair = synchronized(lock) {
            gatingEvent = signal
            matchLocked()
        }
        if (pair != null) {
            val (capture, gating) = pair
            onMatch(capture, gating)
        } else {
            // No match yet — self-clean this signal after the expiry timeout.
            scheduleSignalCleanup()
        }
    }

    /**
     * If both a capture and a gating signal are present, and the gating signal's trigger type
     * matches the capture's [PerfettoResult.startReason], removes and returns both. Returns
     * null if either side is absent or the trigger types disagree — in which case both sides
     * are left in place to wait for their true counterpart (or expire).
     */
    private fun matchLocked(): Pair<PerfettoResult, ProfilerEvent>? {
        val signal = gatingEvent
        val cap = capture
        val matchedPair = if (signal != null && cap != null && cap.startReason == signal.triggerType()) {
            cap to signal
        } else {
            null
        }
        if (matchedPair != null) {
            gatingEvent = null
            capture = null
        }
        return matchedPair
    }

    /**
     * Drops an expired gating signal and returns the expired capture so the caller can delete
     * the artifact file. A capture is expired when `result.start + EXPIRY_TIMEOUT_MS <= deviceNow`
     * — [PerfettoResult.start] is set from [TimeProvider.getDeviceTimestampMillis]. A gating
     * signal is expired when `signal.timestampMs() + EXPIRY_TIMEOUT_MS <= serverNow` — the
     * signal's timestamp is server-time-adjusted (device time plus the server offset), so it
     * must be compared against [TimeProvider.getServerTimestampMillis] rather than device time
     * to avoid clock-drift skewing the expiry window. Returns null if nothing expired.
     */
    fun sweep(deviceNow: Long, serverNow: Long): PerfettoResult? = synchronized(lock) {
        var expired: PerfettoResult? = null
        capture?.let { c ->
            if (c.start + EXPIRY_TIMEOUT_MS <= deviceNow) {
                expired = c
                capture = null
            }
        }
        gatingEvent?.let { s ->
            if (s.timestampMs() + EXPIRY_TIMEOUT_MS <= serverNow) {
                gatingEvent = null
            }
        }
        expired
    }

    /**
     * Runs one expiry sweep at the current time: drops expired items and invokes the
     * [onExpired] callback passed at construction for the expired capture. Also usable as a
     * testable seam to drive the sweep with controlled timestamps.
     */
    fun sweepAndDiscard() {
        val expired = sweep(timeProvider.getDeviceTimestampMillis(), timeProvider.getServerTimestampMillis())
        expired?.let { onExpired?.invoke(it) }
    }

    /**
     * Cancels any pending cleanup tasks and discards any still-pending capture via the
     * [onExpired] callback. Does not shut down [executor], which is externally owned and
     * may be shared with other components. Safe to call once.
     */
    fun stop() {
        synchronized(lock) {
            captureCleanupTask?.cancel(false)
            captureCleanupTask = null
            signalCleanupTask?.cancel(false)
            signalCleanupTask = null
        }
        clear()?.let { onExpired?.invoke(it) }
    }

    /**
     * Returns the held capture and empties both sides. Used on feature stop so no artifact
     * file outlives the profiling feature.
     */
    fun clear(): PerfettoResult? = synchronized(lock) {
        val result = capture
        capture = null
        gatingEvent = null
        result
    }

    private fun scheduleCaptureCleanup() {
        synchronized(lock) {
            captureCleanupTask?.cancel(false)
            captureCleanupTask = executor?.scheduleSafe(
                operationName = OPERATION_NAME_CAPTURE_CLEANUP,
                delay = EXPIRY_TIMEOUT_MS,
                unit = TimeUnit.MILLISECONDS,
                internalLogger = InternalLogger.UNBOUND,
                runnable = { sweepAndDiscard() }
            )
        }
    }

    private fun scheduleSignalCleanup() {
        synchronized(lock) {
            signalCleanupTask?.cancel(false)
            signalCleanupTask = executor?.scheduleSafe(
                operationName = OPERATION_NAME_SIGNAL_CLEANUP,
                delay = EXPIRY_TIMEOUT_MS,
                unit = TimeUnit.MILLISECONDS,
                internalLogger = InternalLogger.UNBOUND,
                runnable = { sweepAndDiscard() }
            )
        }
    }

    /**
     * The [com.datadog.android.profiling.internal.ProfilingStartReason] that corresponds to this gating signal's trigger type, or null
     * if this [ProfilerEvent] subtype is not a valid gating signal (not OOM or Anomaly).
     */
    private fun ProfilerEvent.triggerType(): ProfilingStartReason? = when (this) {
        is RumOomErrorEvent -> ProfilingStartReason.OUT_OF_MEMORY
        is RumAnomalyErrorEvent -> ProfilingStartReason.MEMORY_ANOMALY
        else -> null
    }

    /**
     * The timestamp of this gating signal in milliseconds since epoch. Only valid for the
     * accepted gating signal subtypes ([RumOomErrorEvent] and
     * [RumAnomalyErrorEvent]).
     */
    private fun ProfilerEvent.timestampMs(): Long = when (this) {
        is RumOomErrorEvent -> timestamp
        is RumAnomalyErrorEvent -> timestamp
        else -> 0L
    }

    companion object {
        /**
         * How long a capture or gating signal may wait for its counterpart before it is
         * considered stale and dropped.
         */
        internal const val EXPIRY_TIMEOUT_MS = 5_000L

        private const val OPERATION_NAME_CAPTURE_CLEANUP = "pending_trigger_capture_cleanup"
        private const val OPERATION_NAME_SIGNAL_CLEANUP = "pending_trigger_signal_cleanup"
    }
}
