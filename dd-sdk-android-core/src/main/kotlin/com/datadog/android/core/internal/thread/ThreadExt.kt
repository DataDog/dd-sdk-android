/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * A utility method to perform a Thread.sleep() safely.
 * @return whether the thread was interrupted during the sleep
 */
@Suppress("ReturnCount")
internal fun sleepSafe(durationMs: Long, internalLogger: InternalLogger): Boolean {
    try {
        Thread.sleep(durationMs)
        return false
    } catch (e: InterruptedException) {
        try {
            // Restore the interrupted status
            Thread.currentThread().interrupt()
        } catch (se: SecurityException) {
            DdSdkAndroidCoreLogger(internalLogger).logThreadUnableToSetInterruptedState(throwable = se)
        }
        return true
    } catch (e: IllegalArgumentException) {
        // This means we tried sleeping for a negative time
        DdSdkAndroidCoreLogger(internalLogger).logThreadSleepNegativeTime(throwable = e)
        return false
    }
}

/**
 * Logs any exception raised during the execution. Tested indirectly using
 * the tests of [BackPressureExecutorService] and [LoggingScheduledThreadPoolExecutor].
 */
internal fun loggingAfterExecute(task: Runnable?, t: Throwable?, logger: InternalLogger) {
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
                DdSdkAndroidCoreLogger(logger).logThreadUnableToSetInterruptedState(throwable = se)
            }
        }
    }
    if (throwable != null) {
        DdSdkAndroidCoreLogger(logger).logUncaughtExecutionException(throwable = throwable)
    }
}
