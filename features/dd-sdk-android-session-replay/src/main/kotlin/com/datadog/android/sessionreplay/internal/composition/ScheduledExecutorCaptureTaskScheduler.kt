/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Expiry runs off the main looper so asynchronous generations can time out while UI is busy. */
internal class ScheduledExecutorCaptureTaskScheduler(
    private val executorService: ScheduledExecutorService,
    private val internalLogger: InternalLogger
) : CaptureTaskScheduler {
    override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork {
        val future = executorService.scheduleSafe(
            EXPIRY_CONTEXT,
            delayNs,
            TimeUnit.NANOSECONDS,
            internalLogger,
            Runnable(task)
        ) ?: return CancellableCaptureWork.NONE
        return CancellableCaptureWork { future.cancel(false) }
    }

    override fun shutdown() {
        executorService.shutdownNow()
    }

    private companion object {
        const val EXPIRY_CONTEXT = "Session Replay composition generation expiry"
    }
}
