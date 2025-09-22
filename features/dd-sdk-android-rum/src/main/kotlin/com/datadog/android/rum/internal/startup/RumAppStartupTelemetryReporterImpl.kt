/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore

internal class RumAppStartupTelemetryReporterImpl(
    private val internalLogger: InternalLogger,
    private val sdkCore: InternalSdkCore,
    private val contentProviderCreationTimeNanos: Long,
    private val processStartImportance: Int
): RumAppStartupTelemetryReporter {

    override fun reportTTID(
        info: RumTTIDInfo,
        indexInSession: Int
    ) {
        internalLogger.logMetric(
            messageBuilder = { METRIC_NAME },
            additionalProperties = buildMap {
                put(KEY_METRIC_TYPE, METRIC_NAME)
                put(KEY_SCENARIO, info.scenario.name())
                put(KEY_TTID_DURATION, info.duration.inWholeMilliseconds)
                put(KEY_INDEX_IN_SESSION, indexInSession)

                put(KEY_CP_PROCESS_START_DIFF_NS, contentProviderCreationTimeNanos - sdkCore.appStartTimeNs)
                put(KEY_PROCESS_START_IMPORTANCE, processStartImportance)

                info.scenario.gap()?.let {
                    put(KEY_GAP, it.inWholeMilliseconds)
                }
            },
            samplingRate = 100f,
            creationSampleRate = 100f
        )
    }

    companion object {
        const val METRIC_NAME: String = "rum_ttid_telemetry"

        const val KEY_METRIC_TYPE: String = "metric_type"
        const val KEY_SCENARIO: String = "scenario"
        const val KEY_TTID_DURATION: String = "ttid_duration"
        const val KEY_INDEX_IN_SESSION: String = "index_in_session"
        const val KEY_GAP: String = "gap"
        const val KEY_CP_PROCESS_START_DIFF_NS: String = "cp_process_start_diff_ns"
        const val KEY_PROCESS_START_IMPORTANCE = "process_start_importance"
    }
}

