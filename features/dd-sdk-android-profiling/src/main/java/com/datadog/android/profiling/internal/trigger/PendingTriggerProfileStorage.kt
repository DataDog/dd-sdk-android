/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilerEvent.RumAnomalyErrorEvent
import com.datadog.android.internal.profiling.ProfilerEvent.RumAnrEvent
import com.datadog.android.internal.profiling.ProfilerEvent.RumOomErrorEvent
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.utils.bootNtpOffsetNs
import com.datadog.android.profiling.internal.utils.fileDeleteSafe
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class PendingTriggerProfileStorage(
    private val appContext: Context,
    private val executor: ScheduledExecutorService,
    private val timeProvider: TimeProvider,
    private val internalLogger: InternalLogger? = null,
    internal val callback: Callback
) : PendingTriggerProfiles {
    private val lock = Any()

    @Volatile
    private var profilingResult: PerfettoResult? = null

    @Volatile
    private var rumGatingEvent: ProfilerEvent? = null

    @Volatile
    private var resultCleanupTask: ScheduledFuture<*>? = null

    @Volatile
    private var gatingEventCleanupTask: ScheduledFuture<*>? = null

    override fun setProfilingResult(result: PerfettoResult) {
        var overriddenResult: PerfettoResult? = null
        val pair = synchronized(lock) {
            overriddenResult = profilingResult
            profilingResult = result
            matchResult()
        }
        overriddenResult?.let { fileDeleteSafe(it.resultFilePath, internalLogger) }
        if (pair != null) {
            handleMatch(pair.first, pair.second)
            return
        }
        scheduleResultCleanup()
        if (result.startReason == ProfilingStartReason.OUT_OF_MEMORY) {
            // A previous launch received the RUM gating event but died
            // before this trigger result arrived: match them now instead of waiting for yet
            // another launch.
            matchDeferredOomGatingEvent()?.let { deferredGatingEvent ->
                // The histogram was captured in the boot the gating event was received in, which a
                // device reboot may have ended since.
                persistPendingOomProfile(
                    result = result.copy(bootNtpOffsetNs = deferredGatingEvent.bootNtpOffsetNs),
                    rumErrorId = deferredGatingEvent.rumErrorId,
                    rumContext = deferredGatingEvent.rumContext
                )
                synchronized(lock) {
                    if (profilingResult === result) {
                        profilingResult = null
                    }
                }
                callback.onDeferredOomProfileReady()
            }
        }
    }

    override fun setRumGatingEvent(event: ProfilerEvent) {
        if (event.triggerType() == null) return
        // Persisted immediately: the OS trigger result may only be delivered on a later launch.
        // Skipped when the session's quota is denied, as the profile would be dropped anyway.
        if (event is RumOomErrorEvent && !callback.isSessionQuotaDenied(event.rumContext.sessionId)) {
            ProfilingStorage.setPendingOomGatingEvent(
                appContext,
                PendingOomGatingEvent(
                    rumErrorId = event.id,
                    timestampMs = event.timestamp,
                    rumContext = event.rumContext,
                    bootNtpOffsetNs = timeProvider.bootNtpOffsetNs()
                )
            )
        }
        val pair = synchronized(lock) {
            rumGatingEvent = event
            matchResult()
        }
        if (pair != null) {
            handleMatch(pair.first, pair.second)
        } else {
            scheduleGatingEventCleanup()
        }
    }

    private fun handleMatch(result: PerfettoResult, event: ProfilerEvent) {
        if (event is RumOomErrorEvent) {
            if (callback.isSessionQuotaDenied(event.rumContext.sessionId)) {
                dropOomProfile(result)
            } else {
                // The process is about to die of the OOM: the batch write would not complete and
                // reading the trace would allocate on an exhausted heap, so upload on next launch.
                persistPendingOomProfile(
                    result = result,
                    rumErrorId = event.id,
                    rumContext = event.rumContext
                )
            }
        }
        callback.onMatch(result, event)
    }

    @Suppress("ReturnCount")
    private fun matchDeferredOomGatingEvent(): PendingOomGatingEvent? {
        val deferred = ProfilingStorage.getPendingOomGatingEvent(appContext) ?: return null
        val ageMs = timeProvider.getServerTimestampMillis() - deferred.timestampMs
        if (ageMs >= PENDING_OOM_PROFILE_MAX_AGE_MS) {
            ProfilingStorage.removePendingOomGatingEvent(appContext)
            return null
        }
        return deferred
    }

    private fun persistPendingOomProfile(
        result: PerfettoResult,
        rumErrorId: String,
        rumContext: ProfilingRumContext
    ) {
        ProfilingStorage.setPendingOomProfile(
            appContext,
            PendingOomProfile(
                resultFilePath = result.resultFilePath,
                startMs = result.start,
                endMs = result.end,
                bootNtpOffsetNs = result.bootNtpOffsetNs,
                rumErrorId = rumErrorId,
                rumContext = rumContext
            )
        )
        ProfilingStorage.removePendingOomGatingEvent(appContext)
    }

    private fun dropOomProfile(result: PerfettoResult) {
        internalLogger?.log(
            level = InternalLogger.Level.DEBUG,
            target = InternalLogger.Target.USER,
            messageBuilder = { LOG_OOM_PROFILE_DROPPED_QUOTA_DENIED }
        )
        fileDeleteSafe(result.resultFilePath, internalLogger)
        // The gating event may have been persisted before the quota was denied.
        ProfilingStorage.removePendingOomGatingEvent(appContext)
    }

    private fun matchResult(): Pair<PerfettoResult, ProfilerEvent>? {
        val gatingEvent = rumGatingEvent
        val result = profilingResult
        val matchedPair = if (gatingEvent != null && result != null &&
            result.startReason == gatingEvent.triggerType()
        ) {
            result to gatingEvent
        } else {
            null
        }
        if (matchedPair != null) {
            rumGatingEvent = null
            profilingResult = null
        }
        return matchedPair
    }

    internal fun sweep(deviceNow: Long, serverNow: Long): PerfettoResult? = synchronized(lock) {
        var expired: PerfettoResult? = null
        profilingResult?.let { result ->
            if (result.start + PendingTriggerProfiles.EXPIRY_TIMEOUT_MS <= deviceNow) {
                expired = result
                profilingResult = null
            }
        }
        rumGatingEvent?.let { event ->
            if (event.timestampMs() + PendingTriggerProfiles.EXPIRY_TIMEOUT_MS <= serverNow) {
                rumGatingEvent = null
            }
        }
        expired
    }

    private fun sweepAndDiscard() {
        val expired = sweep(
            deviceNow = timeProvider.getDeviceTimestampMillis(),
            serverNow = timeProvider.getServerTimestampMillis()
        )
        expired?.let { fileDeleteSafe(it.resultFilePath, internalLogger) }
    }

    override fun stop() {
        synchronized(lock) {
            resultCleanupTask?.cancel(false)
            resultCleanupTask = null
            gatingEventCleanupTask?.cancel(false)
            gatingEventCleanupTask = null
        }
        clear()?.let { fileDeleteSafe(it.resultFilePath, internalLogger) }
    }

    internal fun clear(): PerfettoResult? = synchronized(lock) {
        val result = profilingResult
        profilingResult = null
        rumGatingEvent = null
        result
    }

    private fun scheduleResultCleanup() {
        synchronized(lock) {
            resultCleanupTask?.cancel(false)
            resultCleanupTask = executor.scheduleSafe(
                operationName = OPERATION_NAME_RESULT_CLEANUP,
                delay = PendingTriggerProfiles.EXPIRY_TIMEOUT_MS,
                unit = TimeUnit.MILLISECONDS,
                internalLogger = internalLogger ?: InternalLogger.UNBOUND,
                runnable = { sweepAndDiscard() }
            )
        }
    }

    private fun scheduleGatingEventCleanup() {
        synchronized(lock) {
            gatingEventCleanupTask?.cancel(false)
            gatingEventCleanupTask = executor.scheduleSafe(
                operationName = OPERATION_NAME_GATING_EVENT_CLEANUP,
                delay = PendingTriggerProfiles.EXPIRY_TIMEOUT_MS,
                unit = TimeUnit.MILLISECONDS,
                internalLogger = internalLogger ?: InternalLogger.UNBOUND,
                runnable = { sweepAndDiscard() }
            )
        }
    }

    private fun ProfilerEvent.triggerType(): ProfilingStartReason? = when (this) {
        is RumAnrEvent -> ProfilingStartReason.ANR
        is RumOomErrorEvent -> ProfilingStartReason.OUT_OF_MEMORY
        is RumAnomalyErrorEvent -> ProfilingStartReason.MEMORY_ANOMALY
        else -> null
    }

    private fun ProfilerEvent.timestampMs(): Long = when (this) {
        is RumAnrEvent -> startMs
        is RumOomErrorEvent -> timestamp
        is RumAnomalyErrorEvent -> timestamp
        else -> 0L
    }

    internal interface Callback {
        fun onMatch(result: PerfettoResult, event: ProfilerEvent)

        fun onDeferredOomProfileReady()

        fun isSessionQuotaDenied(sessionId: String): Boolean
    }

    companion object {
        private const val OPERATION_NAME_RESULT_CLEANUP = "pending_trigger_result_cleanup"
        private const val OPERATION_NAME_GATING_EVENT_CLEANUP = "pending_trigger_gating_event_cleanup"
        internal const val LOG_OOM_PROFILE_DROPPED_QUOTA_DENIED = "Out of memory profile dropped: quota denied."

        /**
         * How long an OOM profile persisted by a previous process stays worth uploading. Matches
         * profile track intake threshold.
         */
        internal val PENDING_OOM_PROFILE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)
    }
}
