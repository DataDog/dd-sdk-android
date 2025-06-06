/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

import android.os.Handler
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.internal.utils.asString
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource

/**
 * A Runnable running on a background thread detecting ANR on the main thread.
 *
 * It runs in a background thread and schedules regular no-op
 */
internal class ANRDetectorRunnable(
    private val sdkCore: FeatureSdkCore,
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

                // Synchronization is required to use `wait`
                synchronized(callback) {
                    if (!handler.post(callback)) {
                        // callback can't be posted, usually means that the looper is exiting
                        return
                    }
                    callback.wait(anrThresholdMs)

                    if (!callback.wasCalled()) {
                        val anrThread = handler.looper.thread
                        val anrException = ANRException(anrThread)
                        val allThreads = mutableListOf(
                            ThreadDump(
                                name = anrThread.name,
                                state = anrThread.state.asString(),
                                stack = anrException.loggableStackTrace(),
                                crashed = false
                            )
                        ) + safeGetAllStacktraces()
                            .filterKeys { it != anrThread }
                            .filterValues { it.isNotEmpty() }
                            .map {
                                val thread = it.key
                                ThreadDump(
                                    name = thread.name,
                                    state = thread.state.asString(),
                                    stack = thread.stackTrace.loggableStackTrace(),
                                    crashed = false
                                )
                            }
                        GlobalRumMonitor.get(sdkCore).addError(
                            ANR_MESSAGE,
                            RumErrorSource.SOURCE,
                            anrException,
                            mapOf(RumAttributes.INTERNAL_ALL_THREADS to allThreads)
                        )
                        callback.wait()
                    }
                }

                if (anrTestDelayMs > 0) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Delay can't be negative
                    Thread.sleep(anrTestDelayMs)
                }
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    // endregion

    fun stop() {
        shouldStop = true
    }

    private fun safeGetAllStacktraces(): Map<Thread, Array<StackTraceElement>> {
        return try {
            Thread.getAllStackTraces()
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            // coroutines machinery can throw errors here
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to get all stack traces." },
                t
            )
            emptyMap()
        }
    }

    // We need to let this class extend java's Object
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    internal class CallbackRunnable : Object(), Runnable {

        private var called = false

        // Synchronization is required to use `wait`
        @Synchronized
        override fun run() {
            called = true
            notifyAll()
        }

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
