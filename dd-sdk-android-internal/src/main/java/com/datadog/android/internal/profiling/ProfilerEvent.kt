/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Events sent between the RUM feature and the profiling feature.
 */
sealed class ProfilerEvent {
    /**
     * Internal event to stop profiler at Time To Initial Display (TTID) point.
     *
     * @param rumContext RUM context at TTID point.
     * @param vitalId The ID of the TTID vital event.
     * @param vitalName The name of the TTID vital event.
     */
    data class TTID(
        val rumContext: ProfilingRumContext,
        val vitalId: String,
        val vitalName: String?
    ) : ProfilerEvent()

    /**
     * Internal event signalling that TTID has been reached but the RUM session is not tracked
     * (unsampled session). No profiling data will be written.
     */
    object TTIDNotTracked : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature whenever an ANR is detected.
     *
     * @param id The ID of the corresponding RUM error event.
     * @param startMs Start timestamp in milliseconds since epoch (server time-adjusted).
     * @param durationNs Duration of the ANR in nanoseconds.
     * @param rumContext RUM context at the time of the ANR.
     */
    data class RumAnrEvent(
        val id: String,
        val startMs: Long,
        val durationNs: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent()

    /**
     * Sent by the RUM feature to the profiling feature whenever a long task is detected.
     *
     * @param id The ID of the corresponding RUM long task event.
     * @param startMs Start timestamp in milliseconds since epoch (server time-adjusted).
     * @param durationNs Duration of the long task in nanoseconds.
     * @param rumContext RUM context at the time of the long task.
     */
    data class RumLongTaskEvent(
        val id: String,
        val startMs: Long,
        val durationNs: Long,
        val rumContext: ProfilingRumContext
    ) : ProfilerEvent()
}
