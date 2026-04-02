/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import java.util.concurrent.ScheduledExecutorService

internal interface Profiler {

    var internalLogger: InternalLogger?

    val scheduledExecutorService: ScheduledExecutorService

    fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        sdkInstanceNames: Set<String>
    )

    fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        durationMs: Int = 0
    )

    fun stop(sdkInstanceName: String)

    fun isRunning(sdkInstanceName: String): Boolean

    fun registerProfilingCallback(sdkInstanceName: String, callback: ProfilerCallback)

    fun unregisterProfilingCallback(sdkInstanceName: String)

    /**
     * Controls whether an app launch profiling session should extend past the 10-second
     * TTID threshold. Set to `true` when continuous profiling is enabled for the session
     * so the launch window merges into the first continuous cycle.
     */
    fun setExtendLaunchSession(extend: Boolean)
}
