/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

import android.os.Handler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource

/**
 * A Runnable running on a background thread detecting ANR on the main thread.
 *
 * It runs in a background thread and schedules regular no-op
 */
internal class ANRDetectorRunnable(
    private val handler: Handler,
    private val anrThresholdMs: Long = ANR_THRESHOLD_MS,
    private val anrTestDelayMs: Long = ANR_TEST_DELAY_MS
) : Runnable {

    private var shouldStop = false

    // region Runnable

    override fun run() {

        while (!Thread.interrupted()) {
            if (shouldStop) return

            try {
                val callback = CallbackRunnable()

                synchronized(callback) {
                    handler.post(callback)
                    callback.wait(anrThresholdMs)

                    if (!callback.wasCalled()) {
                        GlobalRum.get().addError(
                            ANR_MESSAGE,
                            RumErrorSource.SOURCE,
                            ANRException(handler.looper.thread),
                            emptyMap()
                        )
                        callback.wait()
                    }
                }

                Thread.sleep(anrTestDelayMs)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    // endregion

    @Synchronized
    fun stop() {
        shouldStop = true
    }

    // We need to let this class extend java's Object
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    internal class CallbackRunnable : Object(), Runnable {

        private var called = false

        @Synchronized
        override fun run() {
            called = true
            notifyAll()
        }

        @Synchronized
        fun wasCalled(): Boolean {
            return called
        }
    }

    companion object {
        private const val ANR_THRESHOLD_MS = 5000L
        private const val ANR_TEST_DELAY_MS = 500L

        internal const val ANR_MESSAGE = "Application Not Responding"
    }
}
