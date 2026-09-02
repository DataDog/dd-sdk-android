/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.trigger

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilerEvent.RumAnrEvent
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
internal class PendingTriggerProfilesImpl(
    private val executor: ScheduledExecutorService?,
    private val timeProvider: TimeProvider,
    private val internalLogger: InternalLogger? = null,
    private val onMatch: (PerfettoResult, ProfilerEvent) -> Unit
) : PendingTriggerProfiles {
    private val lock = Any()
    private var profilingResult: PerfettoResult? = null
    private var rumGatingEvent: ProfilerEvent? = null
    private var resultCleanupTask: ScheduledFuture<*>? = null
    private var gatingEventCleanupTask: ScheduledFuture<*>? = null

    override fun addProfilingResult(result: PerfettoResult) {
        var overriddenResult: PerfettoResult? = null
        val pair = synchronized(lock) {
            overriddenResult = profilingResult
            profilingResult = result
            matchResult()
        }
        overriddenResult?.let { safeDelete(it.resultFilePath) }
        if (pair != null) {
            val (matchedResult, gatingEvent) = pair
            onMatch(matchedResult, gatingEvent)
        } else {
            scheduleResultCleanup()
        }
    }

    override fun addRumGatingEvent(event: ProfilerEvent) {
        if (event.triggerType() == null) return
        val pair = synchronized(lock) {
            rumGatingEvent = event
            matchResult()
        }
        if (pair != null) {
            onMatch(pair.first, pair.second)
        } else {
            scheduleGatingEventCleanup()
        }
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
        expired?.let { safeDelete(it.resultFilePath) }
    }

    override fun stop() {
        synchronized(lock) {
            resultCleanupTask?.cancel(false)
            resultCleanupTask = null
            gatingEventCleanupTask?.cancel(false)
            gatingEventCleanupTask = null
        }
        clear()?.let { safeDelete(it.resultFilePath) }
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
            resultCleanupTask = executor?.scheduleSafe(
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
            gatingEventCleanupTask = executor?.scheduleSafe(
                operationName = OPERATION_NAME_GATING_EVENT_CLEANUP,
                delay = PendingTriggerProfiles.EXPIRY_TIMEOUT_MS,
                unit = TimeUnit.MILLISECONDS,
                internalLogger = internalLogger ?: InternalLogger.UNBOUND,
                runnable = { sweepAndDiscard() }
            )
        }
    }

    private fun safeDelete(path: String) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            val deleted = File(path).delete()
            if (!deleted) {
                internalLogger?.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { LOG_FILE_DELETE_FAILED }
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { LOG_FILE_DELETE_FAILED },
                t
            )
        }
    }

    private fun ProfilerEvent.triggerType(): ProfilingStartReason? = when (this) {
        is RumAnrEvent -> ProfilingStartReason.ANR
        else -> null
    }

    private fun ProfilerEvent.timestampMs(): Long = when (this) {
        is RumAnrEvent -> startMs
        else -> 0L
    }

    companion object {
        private const val OPERATION_NAME_RESULT_CLEANUP = "pending_trigger_result_cleanup"
        private const val OPERATION_NAME_GATING_EVENT_CLEANUP = "pending_trigger_gating_event_cleanup"
        private const val LOG_FILE_DELETE_FAILED = "Failed to delete pending trigger trace file."
    }
}
