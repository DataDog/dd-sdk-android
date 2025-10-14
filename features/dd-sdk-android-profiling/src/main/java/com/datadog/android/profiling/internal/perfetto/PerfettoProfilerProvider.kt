/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.perfetto

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilerProvider
import java.util.concurrent.ExecutorService

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class PerfettoProfilerProvider : ProfilerProvider {
    override fun provide(
        internalLogger: InternalLogger,
        timeProvider: TimeProvider,
        profilingExecutor: ExecutorService,
        onProfilingSuccess: (PerfettoResult) -> Unit
    ): Profiler = PerfettoProfiler(
        internalLogger = internalLogger,
        timeProvider = timeProvider,
        profilingExecutor = profilingExecutor,
        onProfilingSuccess = onProfilingSuccess
    )
}
