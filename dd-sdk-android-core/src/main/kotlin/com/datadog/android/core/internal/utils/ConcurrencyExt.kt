/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.v2.api.InternalLogger
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal const val ERROR_TASK_REJECTED = "Unable to schedule %s task on the executor"

/**
 * Executes runnable without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param operationName Name of the task.
 * @param internalLogger Internal logger.
 * @param runnable Task to run.
 */
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
 * Executes runnable without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param operationName Name of the task.
 * @param delay Task scheduling delay.
 * @param unit Delay unit.
 * @param internalLogger Internal logger.
 * @param runnable Task to run.
 */
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
 * Submit runnable without throwing [RejectedExecutionException] if it cannot be accepted
 * for execution.
 *
 * @param operationName Name of the task.
 * @param internalLogger Internal logger.
 * @param runnable Task to run.
 */
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
