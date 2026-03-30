/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.profiling

/**
 * Sent by the RUM feature to the profiling feature whenever an ANR is detected.
 *
 * @param startMs Start timestamp in milliseconds since epoch (server time-adjusted).
 * @param durationNs Duration of the ANR in nanoseconds.
 * @param rumContext RUM context at the time of the ANR.
 */
data class RumAnrEvent(
    val startMs: Long,
    val durationNs: Long,
    val rumContext: ProfilingRumContext
)
