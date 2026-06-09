/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.internal.thread.NamedExecutionUnit
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.Callable
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A single threaded executor service using a BackPressureStrategy.
 */
internal class BackPressureExecutorService(
    val logger: InternalLogger,
    executorContext: String,
    backpressureStrategy: BackPressureStrategy,
    timeProvider: TimeProvider
) : ThreadPoolExecutor(
    CORE_POOL_SIZE,
    CORE_POOL_SIZE,
    THREAD_POOL_MAX_KEEP_ALIVE_MS,
    TimeUnit.MILLISECONDS,
    BackPressuredBlockingQueue(logger, executorContext, backpressureStrategy, timeProvider),
    DatadogThreadFactory(executorContext)
),
    FlushableExecutorService {

    // region ThreadPoolExecutor

    override fun <T> newTaskFor(callable: Callable<T>): RunnableFuture<T> {
        val name = (callable as? NamedExecutionUnit)?.name
        return if (name != null) NamedFutureTask(name, callable) else super.newTaskFor(callable)
    }

    override fun <T> newTaskFor(runnable: Runnable, value: T): RunnableFuture<T> {
        val name = (runnable as? NamedExecutionUnit)?.name
        return if (name != null) NamedFutureTask(name, runnable, value) else super.newTaskFor(runnable, value)
    }

    override fun afterExecute(r: Runnable?, t: Throwable?) {
        super.afterExecute(r, t)
        loggingAfterExecute(r, t, logger)
    }

    // endregion

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
