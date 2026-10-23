/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/** Owns completion delivery until the queue drains, including after the Flags feature stops. */
internal class EvaluationCallbackDispatcher(
    private val logger: InternalLogger,
    private val createWorker: () -> ExecutorService
) : Executor {
    private val lock = Any()
    private val pending = ArrayDeque<Runnable>()
    private var draining = false

    override fun execute(command: Runnable) {
        synchronized(lock) {
            pending.addLast(command)
            if (draining) return
            draining = true
        }
        val worker = try {
            createWorker()
        } catch (exception: SecurityException) {
            onSubmissionFailed(exception)
            return
        }
        try {
            // Only one task enters this fresh worker, so core backpressure cannot discard completions.
            @Suppress("UnsafeThirdPartyFunctionCall") // Submission failures retain the queue for a later dispatch.
            worker.execute(::drain)
        } catch (exception: RejectedExecutionException) {
            onSubmissionFailed(exception)
        } catch (exception: SecurityException) {
            onSubmissionFailed(exception)
        } finally {
            try {
                worker.shutdown()
            } catch (exception: SecurityException) {
                logWorkerFailure(exception)
            }
        }
    }

    private fun onSubmissionFailed(exception: RuntimeException) {
        synchronized(lock) { draining = false }
        logWorkerFailure(exception)
    }

    private fun logWorkerFailure(exception: RuntimeException) {
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
                pending.removeFirstOrNull().also { if (it == null) draining = false }
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
}
