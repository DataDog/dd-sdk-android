/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.os.Handler
import android.os.Looper

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
