/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface Profiler {

    var internalLogger: InternalLogger?

    var onProfilingSuccess: ((PerfettoResult) -> Unit)?

    fun start(appContext: Context)

    fun stop()

    fun isRunning(): Boolean
}
