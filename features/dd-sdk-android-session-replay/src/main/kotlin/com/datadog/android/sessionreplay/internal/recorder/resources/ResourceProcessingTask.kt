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

/** Clears queued work on cancellation so the executor no longer retains its image references. */
internal class ResourceProcessingTask(
    private val cancellation: ResourceCancellation?,
    onCancelled: () -> Unit,
    onFailure: () -> Unit,
    action: () -> Unit
) : Runnable {
    private val lock = Any()
    private var action: (() -> Unit)? = action
    private var onCancelled: (() -> Unit)? = onCancelled
    private var onFailure: (() -> Unit)? = onFailure
    private var unregister: (() -> Unit)? = null
    private var executor: ThreadPoolExecutor? = null

    override fun run() {
        var failureCallback: (() -> Unit)? = null
        val work = synchronized(lock) {
            action.also {
                failureCallback = onFailure
                action = null
                onCancelled = null
                onFailure = null
                executor = null
                unregister?.invoke()
                unregister = null
            }
        }
        var completed = false
        try {
            work?.invoke()
            completed = true
        } finally {
            if (!completed) failureCallback?.invoke()
        }
    }

    @MainThread
    fun execute(executor: ExecutorService, logger: InternalLogger) {
        val registration = cancellation?.register { cancel() }
        val shouldSubmit = synchronized(lock) {
            if (action == null) {
                registration?.invoke()
                false
            } else {
                unregister = registration
                this.executor = executor as? ThreadPoolExecutor
                true
            }
        }
        if (!shouldSubmit) return
        // Submissions are serialized on the main thread; workers can only free queue slots.
        // Refuse full SDK queues before their backpressure policy can silently discard a task.
        @Suppress("UnsafeThirdPartyFunctionCall") // SDK executor queues have a non-throwing capacity query.
        val isQueueFull = (executor as? ThreadPoolExecutor)?.queue?.remainingCapacity() == 0
        if (isQueueFull) {
            cancel()
            return
        }
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // Rejection cancels the task and releases its resources below.
            executor.execute(this)
            // Cancellation can win just before execute() actually enqueues the task.
            if (synchronized(lock) { action == null }) removeFromQueue(executor as? ThreadPoolExecutor)
        } catch (e: RejectedExecutionException) {
            cancel()
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to schedule Session Replay image processing" },
                e,
                onlyOnce = true
            )
        }
    }

    private fun cancel() {
        var taskExecutor: ThreadPoolExecutor? = null
        val cleanup = synchronized(lock) {
            onCancelled.also {
                taskExecutor = executor
                action = null
                onCancelled = null
                onFailure = null
                executor = null
                unregister?.invoke()
                unregister = null
            }
        }
        removeFromQueue(taskExecutor)
        cleanup?.invoke()
    }

    private fun removeFromQueue(executor: ThreadPoolExecutor?) {
        @Suppress("UnsafeThirdPartyFunctionCall") // SDK thread pools use queues supporting removal by identity.
        executor?.remove(this)
    }
}
