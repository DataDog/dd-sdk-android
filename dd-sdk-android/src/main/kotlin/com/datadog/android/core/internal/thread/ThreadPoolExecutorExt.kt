/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal const val MAX_SLEEP_DURATION_IN_MS = 10L

internal fun ThreadPoolExecutor.waitToIdle(timeoutInMs: Long): Boolean {
    val startTime = System.nanoTime()
    val timeoutInNs = TimeUnit.MILLISECONDS.toNanos(timeoutInMs)
    val sleepDurationInMs = timeoutInMs.coerceIn(0, MAX_SLEEP_DURATION_IN_MS)
    do {
        if (this.taskCount - this.completedTaskCount <= 0) {
            return true
        }
        Thread.sleep(sleepDurationInMs)
    } while ((System.nanoTime() - startTime) < timeoutInNs)

    return false
}
