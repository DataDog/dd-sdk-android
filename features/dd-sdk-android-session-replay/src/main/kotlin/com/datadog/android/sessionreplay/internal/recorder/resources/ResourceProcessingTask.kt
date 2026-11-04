/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor

/** Completes captures when their drawing work cannot be scheduled or fails. */
internal class ResourceProcessingTask(
    private val onCancelled: () -> Unit,
    private val onFailure: () -> Unit,
    private val action: () -> Unit
) : Runnable {
    override fun run() {
        var completed = false
        try {
            action()
            completed = true
        } finally {
            if (!completed) onFailure()
        }
    }

    @MainThread
    fun execute(executor: ExecutorService, logger: InternalLogger) {
        // Submissions are serialized on the main thread; workers can only free queue slots.
        // Refuse full SDK queues before their backpressure policy can silently discard a task.
        @Suppress("UnsafeThirdPartyFunctionCall") // SDK executor queues have a non-throwing capacity query.
        val isQueueFull = (executor as? ThreadPoolExecutor)?.queue?.remainingCapacity() == 0
        if (isQueueFull) {
            onCancelled()
            return
        }
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // Rejection finishes the capture below.
            executor.execute(this)
        } catch (e: RejectedExecutionException) {
            onCancelled()
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to schedule Session Replay image processing" },
                e,
                onlyOnce = true
            )
        }
    }
}
