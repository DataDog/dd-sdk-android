/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Profiler stop event.
 */
sealed class ProfilerStopEvent {
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
    ) : ProfilerStopEvent()

    /**
     * Internal event signalling that TTID has been reached but the RUM session is not tracked
     * (unsampled session).
     */
    object TTIDNotTracked : ProfilerStopEvent()
}
