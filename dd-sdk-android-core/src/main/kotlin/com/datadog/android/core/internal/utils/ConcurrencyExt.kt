/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import androidx.annotation.CheckResult
import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal const val ERROR_TASK_REJECTED = "Unable to schedule %s task on the executor"
internal const val ERROR_FUTURE_GET_FAILED = "Unable to get result of the %s task"

/**
 * Executes [Runnable] without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param operationName Name of the task.
 * @param internalLogger Internal logger.
 * @param runnable Task to run.
 */
@InternalApi
fun Executor.executeSafe(
    operationName: String,
    internalLogger: InternalLogger,
    runnable: Runnable
) {
    try {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        execute(runnable)
    } catch (e: RejectedExecutionException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { ERROR_TASK_REJECTED.format(Locale.US, operationName) },
            e
        )
    }
}

/**
 * Executes [Runnable] without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param operationName Name of the task.
 * @param delay Task scheduling delay.
 * @param unit Delay unit.
 * @param internalLogger Internal logger.
 * @param runnable Task to run.
 */
@InternalApi
fun ScheduledExecutorService.scheduleSafe(
    operationName: String,
    delay: Long,
    unit: TimeUnit,
    internalLogger: InternalLogger,
    runnable: Runnable
): ScheduledFuture<*>? {
    return try {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        schedule(runnable, delay, unit)
    } catch (e: RejectedExecutionException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { ERROR_TASK_REJECTED.format(Locale.US, operationName) },
            e
        )
        null
    }
}

/**
 * Submit [Runnable] without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param operationName Name of the task.
 * @param internalLogger Internal logger.
 * @param runnable Task to run.
 */
@InternalApi
@CheckResult
fun ExecutorService.submitSafe(
    operationName: String,
    internalLogger: InternalLogger,
    runnable: Runnable
): Future<*>? {
    return try {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        submit(runnable)
    } catch (e: RejectedExecutionException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { ERROR_TASK_REJECTED.format(Locale.US, operationName) },
            e
        )
        null
    }
}

/**
 * Submit [Callable] without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param T Task result type.
 * @param operationName Name of the task.
 * @param internalLogger Internal logger.
 * @param callable Task to run.
 */
@InternalApi
@CheckResult
fun <T> ExecutorService.submitSafe(
    operationName: String,
    internalLogger: InternalLogger,
    callable: Callable<T>
): Future<T>? {
    return try {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        submit(callable)
    } catch (e: RejectedExecutionException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { ERROR_TASK_REJECTED.format(Locale.US, operationName) },
            e
        )
        null
    }
}

/**
 * Safely unwraps [Future] result without throwing any exception.
 *
 * @param T Task result type.
 * @param operationName Name of the task.
 * @param internalLogger Internal logger.
 */
@InternalApi
fun <T> Future<T>?.getSafe(
    operationName: String,
    internalLogger: InternalLogger
): T? {
    return try {
        this?.get()
    } catch (e: InterruptedException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { ERROR_FUTURE_GET_FAILED.format(Locale.US, operationName) },
            e
        )
        null
    } catch (e: CancellationException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { ERROR_FUTURE_GET_FAILED.format(Locale.US, operationName) },
            e
        )
        null
    } catch (e: ExecutionException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { ERROR_FUTURE_GET_FAILED.format(Locale.US, operationName) },
            e
        )
        null
    }
}
