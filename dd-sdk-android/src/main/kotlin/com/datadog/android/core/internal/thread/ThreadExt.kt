/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * A utility method to perform a Thread.sleep() safely.
 * @return whether the thread was interrupted during the sleep
 */
internal fun sleepSafe(durationMs: Long): Boolean {
    try {
        Thread.sleep(durationMs)
        return false
    } catch (e: InterruptedException) {
        try {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
        } catch (se: SecurityException) {
            sdkLogger.e("Thread was unable to set its own interrupted state", se)
        }
        return true
    } catch (e: IllegalArgumentException) {
        // This means we tried sleeping for a negative time
        sdkLogger.w("Thread tried to sleep for a negative amount of time", e)
        return false
    }
}

/**
 * Logs any exception raised during the execution. Tested indirectly using
 * the tests of [LoggingThreadPoolExecutor] and [LoggingScheduledThreadPoolExecutor].
 */
internal fun loggingAfterExecute(task: Runnable?, t: Throwable?, logger: Logger) {
    var throwable = t
    if (t == null && task is Future<*> && task.isDone) {
        try {
            task.get()
        } catch (ce: CancellationException) {
            throwable = ce
        } catch (ee: ExecutionException) {
            throwable = ee.cause
        } catch (ie: InterruptedException) {
            // ignore/reset
            try {
                Thread.currentThread().interrupt()
            } catch (se: SecurityException) {
                // this should not happen
                sdkLogger.e("Thread was unable to set its own interrupted state", se)
            }
        }
    }
    if (throwable != null) {
        logger.log(
            ERROR_WITH_TELEMETRY_LEVEL,
            ERROR_UNCAUGHT_EXECUTION_EXCEPTION,
            throwable
        )
    }
}

internal const val ERROR_UNCAUGHT_EXECUTION_EXCEPTION =
    "Uncaught exception during the task execution"
