/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

internal class LoggingThreadPoolExecutorTest :
    AbstractLoggingExecutorServiceTest<LoggingThreadPoolExecutor>() {
    override fun createTestedExecutorService(): LoggingThreadPoolExecutor {
        return LoggingThreadPoolExecutor(
            corePoolSize = 1,
            maximumPoolSize = 1,
            keepAliveTime = 100,
            TimeUnit.MILLISECONDS,
            LinkedBlockingDeque(),
            mockInternalLogger
        )
    }
}
