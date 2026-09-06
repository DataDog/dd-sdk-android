/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

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
