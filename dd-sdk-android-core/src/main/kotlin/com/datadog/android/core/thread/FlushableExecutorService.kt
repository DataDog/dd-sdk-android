/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.lint.InternalApi
import java.util.concurrent.ExecutorService

/**
 * An [ExecutorService] which backing queue can be drained to a collection.
 */
@InternalApi
interface FlushableExecutorService : ExecutorService {

    /**
     * Drains the queue backing this [ExecutorService] into the provided mutable collection.
     * After this operation, the executor's queue will be empty, and all the runnable entries added
     * to the destination won't have run yet.
     *
     * @param destination the collection into which [Runnable] in the queue should be drained to.
     */
    fun drainTo(destination: MutableCollection<Runnable>)

    /**
     * A Factory for a [FlushableExecutorService] implementation.
     */
    fun interface Factory {

        /**
         * Create an instance of [FlushableExecutorService].
         * @param internalLogger the internal logger
         * @param executorContext Context to be used for logging and naming threads running on this executor.
         * @param backPressureStrategy the strategy to handle back-pressure
         * @return the instance
         */
        fun create(
            internalLogger: InternalLogger,
            executorContext: String,
            backPressureStrategy: BackPressureStrategy
        ): FlushableExecutorService
    }
}
