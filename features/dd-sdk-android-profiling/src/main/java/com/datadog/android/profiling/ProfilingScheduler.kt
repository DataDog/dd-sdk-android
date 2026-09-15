/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

internal const val PROFILING_SCHEDULER_THREAD_NAME = "datadog-profiling-scheduler"

@Suppress("MagicNumber") // the keep-alive must stay expressed as TimeUnit.SECONDS.toMillis(5)
internal val PROFILING_SCHEDULER_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)

/**
 * Scheduler for the process-wide profiler singleton.
 *
 * This cannot borrow an executor from the SDK core: the profiler is initialized from
 * [DdProfilingContentProvider] before `Datadog.initialize()` runs, and it outlives any single SDK
 * instance, so a core-scoped executor would be shut down underneath it. It therefore builds its
 * own pool, with a core thread that is reclaimed once idle so the profiler holds no thread between
 * profiling windows.
 */
@Suppress("PackageNameVisibility") // lives next to Profiling, which is the only consumer
internal fun createProfilingScheduler(): ScheduledExecutorService {
    @Suppress("UnsafeThirdPartyFunctionCall") // both arguments are safe
    val executor = ScheduledThreadPoolExecutor(
        1,
        ThreadFactory { runnable ->
            @Suppress("UnsafeThirdPartyFunctionCall") // both arguments are safe
            Thread(runnable, PROFILING_SCHEDULER_THREAD_NAME).apply {
                priority = Thread.NORM_PRIORITY
                isDaemon = false
            }
        }
    )
    // allowCoreThreadTimeOut rejects a non-positive keep-alive, and ScheduledThreadPoolExecutor
    // defaults it to 0, so the keep-alive must be set first. A pending task keeps the worker
    // alive regardless: ThreadPoolExecutor#getTask() will not let the last worker exit while the
    // queue is non-empty, including a delayed task that is not yet due.
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive is a positive constant
    executor.setKeepAliveTime(PROFILING_SCHEDULER_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS)
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive was just set to a positive value
    executor.allowCoreThreadTimeOut(true)
    return executor
}
