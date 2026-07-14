/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.telemetry

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.MethodCallSamplingRate

internal class ProfilingTelemetry {

    private val lock = Any()
    private val pendingEvents: MutableList<ProfilingTelemetryEvent> = mutableListOf()

    @Volatile
    var internalLogger: InternalLogger? = null
        set(value) {
            val toFlush: List<ProfilingTelemetryEvent>
            synchronized(lock) {
                field = value
                toFlush = if (value != null && pendingEvents.isNotEmpty()) {
                    val snapshot = pendingEvents.toList()
                    pendingEvents.clear()
                    snapshot
                } else {
                    emptyList()
                }
            }
            value?.let { logger -> toFlush.forEach { dispatch(logger, it) } }
        }

    @Volatile
    var profilingPackageVersionCode: Long = 0L

    fun report(event: ProfilingTelemetryEvent) {
        val logger = synchronized(lock) {
            val current = internalLogger
            if (current == null) {
                pendingEvents.add(event)
                null
            } else {
                current
            }
        }
        logger?.let { dispatch(it, event) }
    }

    private fun dispatch(logger: InternalLogger, event: ProfilingTelemetryEvent) {
        when (event) {
            is ProfilingTelemetryEvent.SessionEnd -> dispatchSessionEnd(logger, event)
            is ProfilingTelemetryEvent.AnrTriggerResult -> dispatchAnrTriggerResult(logger, event)
        }
    }

    private fun dispatchSessionEnd(
        logger: InternalLogger,
        event: ProfilingTelemetryEvent.SessionEnd
    ) {
        logger.logMetric(
            messageBuilder = { TELEMETRY_MSG_PROFILING_SESSION },
            additionalProperties = mapOf(
                KEY_METRIC_TYPE to METRIC_TYPE_PROFILING_SESSION,
                KEY_PROFILING_SESSION to mapOf(
                    KEY_ERROR_CODE to event.errorCode,
                    KEY_START_REASON to event.startReason,
                    KEY_DURATION to event.durationMs,
                    KEY_CALLBACK_DELAY to event.resultCallbackDelayMs,
                    KEY_CLIENT_CLOCK_DRIFT to event.clientClockDriftMs,
                    KEY_ERROR_MESSAGE to event.errorMessage,
                    KEY_FILE_SIZE to event.fileSize,
                    KEY_STOPPED_REASON to event.stopReason,
                    KEY_APP_START_INFO to event.appStartInfo
                ),
                KEY_PROFILING_CONFIG to mapOf(
                    KEY_BUFFER_SIZE to event.bufferSizeKb,
                    KEY_SAMPLING_FREQUENCY to event.samplingFrequencyHz,
                    KEY_PROFILING_PACKAGE_VERSION_CODE to profilingPackageVersionCode
                )
            ),
            samplingRate = MethodCallSamplingRate.ALL.rate
        )
    }

    private fun dispatchAnrTriggerResult(
        logger: InternalLogger,
        event: ProfilingTelemetryEvent.AnrTriggerResult
    ) {
        logger.logMetric(
            messageBuilder = { TELEMETRY_MSG_PROFILING_SESSION },
            additionalProperties = mapOf(
                KEY_METRIC_TYPE to METRIC_TYPE_PROFILING_TRIGGER,
                KEY_PROFILING_SESSION to mapOf(
                    KEY_START_REASON to ANR_PROFILING_TRIGGER_START_REASON,
                    KEY_ERROR_CODE to event.errorCode,
                    KEY_ERROR_MESSAGE to event.errorMessage,
                    KEY_FILE_SIZE to event.fileSize,
                    KEY_CALLBACK_DELAY to event.callbackDelayMs,
                    KEY_CLIENT_CLOCK_DRIFT to event.clientClockDriftMs,
                    KEY_DROPPED_AS_STALE to event.droppedAsStale
                ),
                KEY_PROFILING_CONFIG to mapOf(
                    KEY_PROFILING_PACKAGE_VERSION_CODE to profilingPackageVersionCode
                )
            ),
            samplingRate = MethodCallSamplingRate.ALL.rate
        )
    }

    companion object {
        internal const val TELEMETRY_MSG_PROFILING_SESSION = "[Mobile Metric] Profiling Session"

        internal const val KEY_METRIC_TYPE = "metric_type"
        internal const val KEY_PROFILING_SESSION = "profiling_session"
        internal const val KEY_PROFILING_CONFIG = "profiling_config"

        internal const val KEY_ERROR_CODE = "error_code"
        internal const val KEY_ERROR_MESSAGE = "error_message"
        internal const val KEY_FILE_SIZE = "file_size"
        internal const val KEY_START_REASON = "start_reason"
        internal const val KEY_DURATION = "duration"
        internal const val KEY_CALLBACK_DELAY = "callback_delay_ms"
        internal const val KEY_CLIENT_CLOCK_DRIFT = "client_clock_drift_ms"
        internal const val KEY_PROFILING_PACKAGE_VERSION_CODE = "profiling_package_version_code"
        internal const val KEY_STOPPED_REASON = "stopped_reason"
        internal const val KEY_APP_START_INFO = "app_start_info"
        internal const val KEY_BUFFER_SIZE = "buffer_size"
        internal const val KEY_SAMPLING_FREQUENCY = "sampling_frequency"
        internal const val KEY_DROPPED_AS_STALE = "dropped_as_stale"

        internal const val METRIC_TYPE_PROFILING_SESSION = "profiling session"
        internal const val METRIC_TYPE_PROFILING_TRIGGER = "profiling trigger"
        internal const val ANR_PROFILING_TRIGGER_START_REASON = "anr_profiling_trigger"

        internal const val STOPPED_REASON_MANUAL = "manual"
        internal const val STOPPED_REASON_TIMEOUT = "timeout"
        internal const val STOPPED_REASON_ERROR = "error"
    }
}
