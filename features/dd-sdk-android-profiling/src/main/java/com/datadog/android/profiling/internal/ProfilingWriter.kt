/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface ProfilingWriter {

    fun write(
        profilingResult: PerfettoResult,
        longTasks: List<ProfilerEvent.RumLongTaskEvent>,
        anrEvents: List<ProfilerEvent.RumAnrEvent>,
        vitalEvents: List<ProfilerEvent.RumVitalEvent>
    )

    /**
     * Deletes the profiling result file without uploading it. Used when a profile is dropped
     * before it reaches the write path (e.g. quota denied), so the trace file is not leaked.
     */
    fun discard(profilingResult: PerfettoResult)
}
