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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Volatile
    private var shouldStop = false

    // region Runnable

    override fun run() {
        while (!Thread.currentThread().isInterrupted) {
            if (shouldStop) return

            try {
                @Suppress("UnsafeThirdPartyFunctionCall") // argument is positive
                val callbackDone = CountDownLatch(1)
                val callback = CallbackRunnable(callbackDone)
                if (!handler.post(callback)) {
                    // callback can't be posted, usually means that the looper is exiting
                    return
                }

                if (!callbackDone.await(anrThresholdMs, TimeUnit.MILLISECONDS)) {
                    reportAnr()
                    waitForAnrResolution(callbackDone)
                }

                if (anrTestDelayMs > 0) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Delay can't be negative
                    Thread.sleep(anrTestDelayMs)
                }
            } catch (e: InterruptedException) {
                // If SecurityException is thrown, let it propagate and kill this thread
                // New one will be created on executor
                @Suppress("UnsafeThirdPartyFunctionCall")
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    // endregion

    fun stop() {
        shouldStop = true
    }

    private fun reportAnr() {
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
    }

    private fun waitForAnrResolution(callbackDone: CountDownLatch) {
        try {
            callbackDone.await()
        } catch (ie: InterruptedException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Interrupted while waiting for ANR resolution." },
                ie
            )
            try {
                Thread.currentThread().interrupt()
            } catch (se: SecurityException) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Failed to restore interrupted state during ANR resolution." },
                    se
                )
            }
        }
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

    internal class CallbackRunnable(
        private val callbackDone: CountDownLatch
    ) : Runnable {

        override fun run() {
            callbackDone.countDown()
        }
    }

    companion object {
        internal const val ANR_THRESHOLD_MS = 5000L
        private const val ANR_TEST_DELAY_MS = 500L

        internal const val ANR_MESSAGE = "Application Not Responding"
    }
}
