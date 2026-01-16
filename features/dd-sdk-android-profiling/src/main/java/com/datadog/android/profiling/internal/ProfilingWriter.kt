/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.profiling.LongTaskRumContext
import com.datadog.android.internal.profiling.TTIDRumContext
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface ProfilingWriter {

    fun write(
        profilingResult: PerfettoResult,
        ttidRumContext: TTIDRumContext
    )

    fun write(
        profilingResult: PerfettoResult,
        anrLongTaskContexts: List<LongTaskRumContext>
    )
}
