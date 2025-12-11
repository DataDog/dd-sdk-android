/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal const val MAX_SLEEP_DURATION_IN_MS = 10L

internal fun ThreadPoolExecutor.waitToIdle(
    timeoutInMs: Long,
    internalLogger: InternalLogger,
    timeProvider: TimeProvider
): Boolean {
    val startTime = timeProvider.getDeviceElapsedTimeNs()
    val timeoutInNs = TimeUnit.MILLISECONDS.toNanos(timeoutInMs)
    val sleepDurationInMs = timeoutInMs.coerceIn(0, MAX_SLEEP_DURATION_IN_MS)
    var interrupted: Boolean
    do {
        if (isIdle()) {
            return true
        }
        interrupted = sleepSafe(sleepDurationInMs, internalLogger)
    } while (((timeProvider.getDeviceElapsedTimeNs() - startTime) < timeoutInNs) && !interrupted)

    return isIdle()
}

internal fun ThreadPoolExecutor.isIdle(): Boolean {
    return (this.taskCount - this.completedTaskCount <= 0)
}
