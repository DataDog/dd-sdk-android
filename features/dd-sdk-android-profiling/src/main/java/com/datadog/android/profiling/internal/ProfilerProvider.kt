/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import java.util.concurrent.ExecutorService

internal interface ProfilerProvider {
    fun provide(
        internalLogger: InternalLogger,
        timeProvider: TimeProvider,
        profilingExecutor: ExecutorService,
        onProfilingSuccess: (PerfettoResult) -> Unit
    ): Profiler
}
