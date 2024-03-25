/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.thread.FlushableExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class BackPressureExecutorService(
    val logger: InternalLogger,
    backpressureStrategy: BackPressureStrategy
) : ThreadPoolExecutor(
    CORE_POOL_SIZE,
    CORE_POOL_SIZE,
    THREAD_POOL_MAX_KEEP_ALIVE_MS,
    TimeUnit.MILLISECONDS,
    BackPressuredBlockingQueue(logger, backpressureStrategy)
),
    FlushableExecutorService {

    // region FlushableExecutorService

    @Suppress("TooGenericExceptionCaught")
    override fun drainTo(destination: MutableCollection<Runnable>) {
        try {
            queue.drainTo(destination)
        } catch (e: IllegalArgumentException) {
            onDrainException(e)
        } catch (e: NullPointerException) {
            onDrainException(e)
        } catch (e: UnsupportedOperationException) {
            onDrainException(e)
        } catch (e: ClassCastException) {
            onDrainException(e)
        }
    }

    // endregion

    // region ThreadPoolExecutor

    override fun afterExecute(r: Runnable?, t: Throwable?) {
        super.afterExecute(r, t)
        loggingAfterExecute(r, t, logger)
    }

    // endregion

    private fun onDrainException(e: RuntimeException) {
        logger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { "Unable to drain BackPressureExecutorService queue" },
            e
        )
    }

    companion object {
        private const val CORE_POOL_SIZE = 1
        private val THREAD_POOL_MAX_KEEP_ALIVE_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
