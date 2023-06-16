/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.v2.api.InternalLogger
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * [ScheduledThreadPoolExecutor] with a [ScheduledThreadPoolExecutor.afterExecute] hook,
 * which will log any unhandled exception raised.
 *
 * @param corePoolSize see [ScheduledThreadPoolExecutor] docs.
 * @param logger [InternalLogger] instance.
 */
class LoggingScheduledThreadPoolExecutor(
    corePoolSize: Int,
    private val logger: InternalLogger
) :
    ScheduledThreadPoolExecutor(corePoolSize) {

    /** @inheritdoc */
    override fun afterExecute(r: Runnable?, t: Throwable?) {
        @Suppress("UnsafeThirdPartyFunctionCall") // we just call super
        super.afterExecute(r, t)
        loggingAfterExecute(r, t, logger)
    }
}
