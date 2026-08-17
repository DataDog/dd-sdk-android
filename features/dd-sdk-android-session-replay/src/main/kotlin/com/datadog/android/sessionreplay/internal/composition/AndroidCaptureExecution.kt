/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.os.Handler
import android.os.Looper
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class HandlerCaptureMainThreadExecutor(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : CaptureMainThreadExecutor {
    override fun execute(task: () -> Unit): CancellableCaptureWork {
        val runnable = Runnable(task)
        return if (handler.post(runnable)) {
            CancellableCaptureWork { handler.removeCallbacks(runnable) }
        } else {
            CancellableCaptureWork.NONE
        }
    }
}

internal class HandlerCaptureTaskScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : CaptureTaskScheduler {
    override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork {
        val runnable = Runnable(task)
        val posted = if (delayNs <= 0) {
            handler.post(runnable)
        } else {
            handler.postDelayed(runnable, delayNs.toDelayMillis())
        }
        return if (posted) {
            CancellableCaptureWork { handler.removeCallbacks(runnable) }
        } else {
            CancellableCaptureWork.NONE
        }
    }

    private fun Long.toDelayMillis(): Long {
        val delayMs = TimeUnit.NANOSECONDS.toMillis(this)
        return if (delayMs < 1L) 1L else delayMs
    }
}

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

internal class TimeProviderCaptureTimeProvider(
    private val timeProvider: TimeProvider
) : CaptureTimeProvider {
    override fun elapsedRealtimeNanos(): Long = timeProvider.getDeviceElapsedTimeNanos()
}
