/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import android.os.Handler
import android.os.Looper
import com.datadog.android.api.InternalLogger
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/** Owns completion delivery until the queue drains, including after the Flags feature stops. */
internal class EvaluationCallbackDispatcher(
    private val logger: InternalLogger,
    private val scheduleRetry: (Runnable, Long) -> Boolean = { command, delay ->
        Handler(Looper.getMainLooper()).postDelayed(command, delay)
    },
    private val createWorker: () -> ExecutorService
) : Executor {
    private val lock = Any()
    private val pending = ArrayDeque<Runnable>()
    private var draining = false
    private var failureReported = false

    override fun execute(command: Runnable) {
        synchronized(lock) {
            pending.addLast(command)
            if (draining) return
            draining = true
        }
        startWorker()
    }

    private fun startWorker() {
        val worker = try {
            createWorker()
        } catch (exception: SecurityException) {
            onSubmissionFailed(exception)
            return
        }
        try {
            // Only one task enters this fresh worker, so core backpressure cannot discard completions.
            @Suppress("UnsafeThirdPartyFunctionCall") // Submission failures schedule another worker-start attempt.
            worker.execute(::drain)
        } catch (exception: RejectedExecutionException) {
            onSubmissionFailed(exception)
        } catch (exception: SecurityException) {
            onSubmissionFailed(exception)
        } catch (error: OutOfMemoryError) {
            // Thread.start() reports a thread-limit failure this way; it may be transient.
            onSubmissionFailed(error)
        } finally {
            try {
                worker.shutdown()
            } catch (exception: SecurityException) {
                logWorkerFailure(exception)
            }
        }
    }

    private fun onSubmissionFailed(exception: Throwable) {
        logWorkerFailure(exception)
        // The main looper retries worker startup only. Customer callbacks stay on the worker.
        if (!scheduleRetry(Runnable(::startWorker), RETRY_DELAY_MS)) {
            synchronized(lock) { draining = false }
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Unable to retry the feature flags callback worker: the main looper is not accepting work." },
                onlyOnce = true
            )
        }
    }

    private fun logWorkerFailure(exception: Throwable) {
        synchronized(lock) {
            if (failureReported) return
            failureReported = true
        }
        logger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { "Unable to start or stop the feature flags callback worker." },
            exception
        )
    }

    private fun drain() {
        while (true) {
            val command = synchronized(lock) {
                pending.removeFirstOrNull().also {
                    if (it == null) {
                        draining = false
                        failureReported = false
                    }
                }
            } ?: return
            try {
                command.run()
            } catch (
                // Customer callbacks must not prevent later completions.
                @Suppress("TooGenericExceptionCaught")
                throwable: Throwable
            ) {
                logger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { "A feature flags evaluation callback failed." },
                    throwable
                )
            }
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 1_000L
    }
}
