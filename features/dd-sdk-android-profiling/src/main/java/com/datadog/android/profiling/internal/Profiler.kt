/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import java.util.concurrent.ScheduledExecutorService

internal interface Profiler {

    val timeProvider: MutableTimeProvider

    var internalLogger: InternalLogger?

    val scheduledExecutorService: ScheduledExecutorService

    fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        durationMs: Int = 0
    )

    fun stop()

    fun isRunning(): Boolean

    fun registerProfilingCallback(appContext: Context, callback: ProfilerCallback)

    fun unregisterProfilingCallback(appContext: Context)

    /**
     * Controls whether an app launch profiling session should extend past the 10-second
     * TTID threshold. Set to `true` when continuous profiling is enabled for the session
     * so the launch window merges into the first continuous cycle.
     */
    fun setExtendLaunchSession(extend: Boolean)

    /**
     * Resolves the version of the profiling system package, if it is not known yet, and keeps it
     * for the whole process lifetime. The profiler can be started before the SDK is initialized,
     * so it resolves the version on its own; this only makes sure it is also known when profiling
     * never starts.
     */
    fun resolveProfilingPackageVersionCode(appContext: Context)
}
