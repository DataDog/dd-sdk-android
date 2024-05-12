/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import java.util.Locale

/**
 * Monitors the number of calls per event type within a time period and warns when a threshold is exceeded.
 *
 * @param internalLogger instance of the logger
 * @param rumCallMonitorMap map to track the number of calls per entry in a given time period
 * @param timePeriodMs the time period during which we count whether we've exceeded the threshold of calls
 * @param maxCallsThreshold the maximum number of calls allowed in the time period, beyond which emit a warning
 */
internal class RumEventCallMonitor(
    private val internalLogger: InternalLogger,
    private val rumCallMonitorMap: MutableMap<String, RumEventCallMonitorEntry>,
    private val timePeriodMs: Long,
    private val maxCallsThreshold: Int
) {

    @Synchronized
    internal fun trackCallsAndWarnIfNecessary(eventType: String) {
        addToMapIfMissing(eventType)
        resetInMapIfTimePeriodEnded(eventType)
        incrementEventCallsCounter(eventType)
        logWarningMessageIfBeyondThreshold(eventType)
    }

    private fun incrementEventCallsCounter(eventType: String) {
        val mapEntry = rumCallMonitorMap[eventType] ?: return
        mapEntry.numCallsInTimePeriod.incrementAndGet()
    }

    private fun resetInMapIfTimePeriodEnded(eventType: String) {
        val mapEntry = rumCallMonitorMap[eventType] ?: return
        val callPeriodTimeMs = mapEntry.timePeriodStartTimeMs.get()

        if (isPastTimePeriod(callPeriodTimeMs)) {
            rumCallMonitorMap[eventType] = RumEventCallMonitorEntry()
        }
    }

    private fun addToMapIfMissing(eventType: String) {
        if (!rumCallMonitorMap.containsKey(eventType)) {
            rumCallMonitorMap[eventType] = RumEventCallMonitorEntry()
        }
    }

    private fun logWarningMessageIfBeyondThreshold(eventType: String) {
        val mapEntry = rumCallMonitorMap[eventType] ?: return
        val numCallsInTimeSpan = mapEntry.numCallsInTimePeriod.get()

        if (numCallsInTimeSpan > maxCallsThreshold) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                {
                    TOO_MANY_RUM_EVENT_CALLS.format(
                        Locale.US,
                        maxCallsThreshold,
                        eventType,
                        timePeriodMs
                    )
                }
            )
        }
    }

    private fun isPastTimePeriod(callPeriodTimeMs: Long): Boolean {
        return System.currentTimeMillis() - callPeriodTimeMs > this.timePeriodMs
    }

    internal companion object {
        internal const val DEFAULT_RUM_NUM_CALLS_WARNING_THRESHOLD = 100
        internal const val DEFAULT_RUM_CALLS_TIMESPAN_MS = 30000L // 30 seconds

        @VisibleForTesting
        internal const val TOO_MANY_RUM_EVENT_CALLS =
            "More than %s RUM event calls for %s within %s seconds - this may impact performance"
    }
}
