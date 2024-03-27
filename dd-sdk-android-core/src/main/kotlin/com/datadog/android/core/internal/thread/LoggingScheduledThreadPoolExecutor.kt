/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureStrategy
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * [ScheduledThreadPoolExecutor] with a [ScheduledThreadPoolExecutor.afterExecute] hook,
 * which will log any unhandled exception raised.
 *
 * @param corePoolSize see [ScheduledThreadPoolExecutor] docs.
 * @param logger [InternalLogger] instance.
 * @param backPressureStrategy the back pressure strategy to notify dropped items
 */
// TODO RUM-3704 create an implementation that uses the backpressure strategy
internal class LoggingScheduledThreadPoolExecutor(
    corePoolSize: Int,
    private val logger: InternalLogger,
    private val backPressureStrategy: BackPressureStrategy
) : ScheduledThreadPoolExecutor(
    corePoolSize,
    RejectedExecutionHandler { r, _ ->
        if (r != null) {
            logger.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { "Dropped scheduled item in LoggingScheduledThreadPoolExecutor queue: $r" },
                throwable = null,
                onlyOnce = false
            )
            backPressureStrategy.onItemDropped(r)
        }
    }
) {

    /** @inheritdoc */
    override fun afterExecute(r: Runnable?, t: Throwable?) {
        super.afterExecute(r, t)
        loggingAfterExecute(r, t, logger)
    }
}
