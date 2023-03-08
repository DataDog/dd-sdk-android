/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils

import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.v2.api.InternalLogger
import java.util.Locale
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal const val ERROR_TASK_REJECTED = "Unable to schedule %s task on the executor"

// TODO RUMM-0000 this is a copy of the similar function from core package. Have to make it this
//  way because API surface task is failing trying to parse star-projection here. Will be solved later.
internal fun ScheduledExecutorService.scheduleSafe(
    operationName: String,
    delay: Long,
    unit: TimeUnit,
    runnable: Runnable
): ScheduledFuture<*>? {
    return try {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        schedule(runnable, delay, unit)
    } catch (e: RejectedExecutionException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            ERROR_TASK_REJECTED.format(Locale.US, operationName),
            e
        )
        null
    }
}
