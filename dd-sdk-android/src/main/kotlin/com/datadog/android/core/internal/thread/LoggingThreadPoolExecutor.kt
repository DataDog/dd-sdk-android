/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.v2.api.InternalLogger
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * [ThreadPoolExecutor] with a [ThreadPoolExecutor.afterExecute] hook, which will log any unhandled
 * exception raised.
 */
internal class LoggingThreadPoolExecutor(
    corePoolSize: Int,
    maximumPoolSize: Int,
    keepAliveTime: Long,
    unit: TimeUnit?,
    workQueue: BlockingQueue<Runnable>?,
    private val logger: InternalLogger
) : ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue) {

    override fun afterExecute(r: Runnable?, t: Throwable?) {
        @Suppress("UnsafeThirdPartyFunctionCall") // we just call super
        super.afterExecute(r, t)
        loggingAfterExecute(r, t, logger)
    }
}
