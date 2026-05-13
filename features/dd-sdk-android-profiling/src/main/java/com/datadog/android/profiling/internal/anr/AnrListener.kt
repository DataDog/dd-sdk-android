/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.anr

import com.datadog.android.internal.profiling.ProfilingThreadDump

internal fun interface AnrListener {
    fun onAnrDetected(
        detectedAtMs: Long,
        anrThreadStack: List<StackTraceElement>,
        allThreads: List<ProfilingThreadDump>
    )
}
