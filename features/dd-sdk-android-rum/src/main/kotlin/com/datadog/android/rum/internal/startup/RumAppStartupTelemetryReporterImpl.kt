/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import com.datadog.android.api.InternalLogger

internal class RumAppStartupTelemetryReporterImpl(
    private val internalLogger: InternalLogger,
    private val appStartupTimeNs: Long,
    private val contentProviderCreationTimeNs: Long,
    private val processStartImportance: Int
) : RumAppStartupTelemetryReporter {

    override fun reportTTID(
        info: RumTTIDInfo,
        indexInSession: Int
    ) {
        internalLogger.logMetric(
            messageBuilder = { METRIC_NAME },
            additionalProperties = mapOf(
                KEY_METRIC_TYPE to METRIC_TYPE_VALUE,
                KEY_APP_LAUNCH_TTID to buildMap {
                    put(KEY_SCENARIO, info.scenario.name)
                    put(KEY_TTID_DURATION_NS, info.durationNs)
                    put(KEY_INDEX_IN_SESSION, indexInSession)

                    put(KEY_CP_PROCESS_START_DIFF_NS, contentProviderCreationTimeNs - appStartupTimeNs)
                    put(KEY_PROCESS_START_IMPORTANCE, processStartImportance)

                    put(KEY_HAS_SAVED_INSTANCE_STATE, info.scenario.hasSavedInstanceStateBundle)

                    info.scenario.appStartActivityOnCreateGapNs?.let {
                        put(KEY_GAP_NS, it)
                    }
                }
            ),
            samplingRate = SAMPLING_RATE
        )
    }

    companion object {
        const val METRIC_NAME: String = "[Mobile Metric] App Launch TTID"

        const val KEY_APP_LAUNCH_TTID = "app_launch_ttid"

        const val KEY_METRIC_TYPE: String = "metric_type"
        const val METRIC_TYPE_VALUE: String = "app launch ttid"
        const val KEY_SCENARIO: String = "scenario"
        const val KEY_TTID_DURATION_NS: String = "duration_ns"
        const val KEY_INDEX_IN_SESSION: String = "index_in_session"
        const val KEY_GAP_NS: String = "app_start_activity_on_create_gap_ns"
        const val KEY_CP_PROCESS_START_DIFF_NS: String = "cp_process_start_diff_ns"
        const val KEY_PROCESS_START_IMPORTANCE = "process_start_importance"
        const val KEY_HAS_SAVED_INSTANCE_STATE = "has_saved_instance_state"

        private const val SAMPLING_RATE = 15.0f
    }
}
