/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.api.InternalLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

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

    fun execute(executor: ExecutorService, logger: InternalLogger) {
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
