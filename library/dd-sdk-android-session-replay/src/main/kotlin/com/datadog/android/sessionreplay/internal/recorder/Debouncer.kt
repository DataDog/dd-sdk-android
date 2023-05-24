/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

internal class Debouncer(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val maxRecordDelayInNs: Long = MAX_DELAY_THRESHOLD_NS
) {

    private var lastTimeRecordWasPerformed = 0L
    private var firstRequest = true

    internal fun debounce(runnable: Runnable) {
        if (firstRequest) {
            // we will initialize the lastTimeRecordWasPerformed here to the current time in nano
            // reason why we are not initializing this in the constructor is that in case the
            // component was initialized earlier than the first debounce request was requested
            // it will execute the runnable directly and will not pass through the handler.
            lastTimeRecordWasPerformed = System.nanoTime()
            firstRequest = false
        }
        handler.removeCallbacksAndMessages(null)
        val timePassedSinceLastExecution = System.nanoTime() - lastTimeRecordWasPerformed
        if (timePassedSinceLastExecution >= maxRecordDelayInNs) {
            executeRunnable(runnable)
        } else {
            handler.postDelayed({ executeRunnable(runnable) }, DEBOUNCE_TIME_IN_MS)
        }
    }

    private fun executeRunnable(runnable: Runnable) {
        runnable.run()
        lastTimeRecordWasPerformed = System.nanoTime()
    }

    companion object {
        // one frame time
        private val MAX_DELAY_THRESHOLD_NS: Long = TimeUnit.MILLISECONDS.toNanos(64)

        // one frame time
        internal const val DEBOUNCE_TIME_IN_MS: Long = 64
    }
}
