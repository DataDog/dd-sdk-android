/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Profiler event.
 */
sealed class ProfilerEvent {
    /**
     * Internal event to stop profiler at Time To Initial Display (TTID) point.
     *
     * @param rumContext RUM context at TTID point. Will be null if RUM session is not sampled.
     */
    data class TTIDStop(
        val rumContext: TTIDRumContext? = null
    ) : ProfilerEvent()

    data class AddLongTask(
        val longTaskRumContext: LongTaskRumContext
    ) : ProfilerEvent()
}

/**
 * RUM context at TTID mark.
 *
 * @param applicationId The Id of the application of RUM.
 * @param sessionId The Id of the RUM session where TTID is captured
 * @param vitalId The Id of the TTID vital event
 * @param viewId The Id of the view where TTID is captured
 * @param viewName The name of the view where TTID is captured
 */
data class TTIDRumContext(
    val applicationId: String,
    val sessionId: String,
    val vitalId: String,
    val viewId: String?,
    val viewName: String?
)

data class LongTaskRumContext(
    val applicationId: String,
    val sessionId: String,
    val longTaskId: String,
    val viewId: String?,
    val viewName: String?
)
