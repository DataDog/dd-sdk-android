/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal const val MAX_SLEEP_DURATION_IN_MS = 10L

internal fun ThreadPoolExecutor.waitToIdle(
    timeoutInMs: Long,
    internalLogger: InternalLogger,
    timeProvider: TimeProvider
): Boolean {
    val startTime = timeProvider.getDeviceElapsedTimeNanos()
    val timeoutInNs = TimeUnit.MILLISECONDS.toNanos(timeoutInMs)
    val sleepDurationInMs = timeoutInMs.coerceIn(0, MAX_SLEEP_DURATION_IN_MS)
    var interrupted: Boolean
    do {
        if (isIdle()) {
            return true
        }
        interrupted = sleepSafe(sleepDurationInMs, internalLogger)
    } while (((timeProvider.getDeviceElapsedTimeNanos() - startTime) < timeoutInNs) && !interrupted)

    return isIdle()
}

internal fun ThreadPoolExecutor.isIdle(): Boolean {
    return (this.taskCount - this.completedTaskCount <= 0)
}

@Suppress("MagicNumber") // the keep-alive value must stay expressed as TimeUnit.SECONDS.toMillis(5)
internal val IDLE_THREAD_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)

/**
 * Lets the single core thread of a Datadog-owned pool die once it has been idle for
 * [IDLE_THREAD_KEEP_ALIVE_MS], so sparse consumers stop holding a permanently-alive thread.
 *
 * A worker is only reclaimed when the queue is empty: [ThreadPoolExecutor.getTask] refuses to
 * let the last worker exit while any task is pending, including a delayed task that is not yet
 * due. So one-shot consumers release their thread, while periodic consumers keep theirs and see
 * no behavior change.
 *
 * This is a no-op for anything other than a Datadog-owned pool: `FlushableExecutorService.Factory`
 * is public API, and a customer-supplied executor must keep whatever pool policy its author chose.
 */
internal fun ExecutorService.enableIdleThreadTimeout() {
    val executor: ThreadPoolExecutor = when (this) {
        is BackPressureExecutorService -> this
        is LoggingScheduledThreadPoolExecutor -> this
        else -> return
    }
    // ScheduledThreadPoolExecutor defaults keep-alive to 0, and allowCoreThreadTimeOut rejects a
    // non-positive keep-alive, so the keep-alive must be set first.
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive is a positive constant
    executor.setKeepAliveTime(IDLE_THREAD_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS)
    @Suppress("UnsafeThirdPartyFunctionCall") // keep-alive was just set to a positive value
    executor.allowCoreThreadTimeOut(true)
}
