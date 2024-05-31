/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureStrategy
import java.util.concurrent.ScheduledExecutorService

/**
 * A factory for [ScheduledExecutorService].
 */
internal fun interface ScheduledExecutorServiceFactory {

    /**
     * Create an instance of [ScheduledExecutorService].
     * @param internalLogger the internal logger
     * @param executorContext Context to be used for logging and naming threads running on this executor.
     * @param backPressureStrategy the strategy to handle back-pressure
     * @return the instance
     */
    fun create(
        internalLogger: InternalLogger,
        executorContext: String,
        backPressureStrategy: BackPressureStrategy
    ): ScheduledExecutorService
}
