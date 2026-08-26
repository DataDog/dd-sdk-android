/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.content.Context
import android.os.Looper
import android.util.Printer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy.CompositePrinter.addPrinter
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy.CompositePrinter.printers
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy.CompositePrinter.println
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy.CompositePrinter.removePrinter
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.tracking.TrackingStrategy
import java.util.concurrent.TimeUnit

internal class MainLooperLongTaskStrategy(internal val thresholdMs: Long) : Printer, TrackingStrategy {

    private lateinit var sdkCore: SdkCore
    private val thresholdNs = TimeUnit.MILLISECONDS.toNanos(thresholdMs)

    @Volatile
    private var state = DispatcherState(thresholdNs)

    // region TrackingStrategy
    @AnyThread
    override fun register(sdkCore: SdkCore, context: Context) {
        this.sdkCore = sdkCore
        state = DispatcherState(thresholdNs)
        addPrinter(this)
    }

    @AnyThread
    override fun unregister(context: Context?) {
        removePrinter(this)
    }

    // endregion

    // region Printer
    @MainThread
    override fun println(message: String?) {
        if (message == null || !this::sdkCore.isInitialized) return
        val now = sdkCore.getDeviceElapsedTimeNanos() ?: return
        when {
            message.startsWith(PREFIX_START) -> state.onStart(message, now)
            message.startsWith(PREFIX_END) -> state.onFinish(now)?.let { longTask ->
                (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.addLongTask(
                    longTask.durationNs,
                    longTask.target
                )
            }
        }
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MainLooperLongTaskStrategy

        return thresholdMs == other.thresholdMs
    }

    override fun hashCode(): Int = thresholdMs.hashCode()

    override fun toString(): String = "MainLooperLongTaskStrategy($thresholdMs)"

    // endregion

    companion object {
        private const val PREFIX_START = ">>>>> Dispatching to "
        private const val PREFIX_END = "<<<<< Finished to "

        // a start that has not happened yet sits infinitely far in the future, so any duration
        // measured against it comes out negative instead of relying on signed overflow
        private const val NOT_STARTED = Long.MAX_VALUE

        private fun SdkCore.getDeviceElapsedTimeNanos(): Long? =
            (this as? FeatureSdkCore)?.timeProvider?.getDeviceElapsedTimeNanos()
    }

    /**
     * Holds the dispatch currently in flight. A fresh instance is published on every [register], so
     * these fields are only ever written and read from the main thread (the [Looper] printer) and
     * need no synchronization of their own.
     */
    private class DispatcherState(private val thresholdNS: Long) {
        private var message: String = ""
        private var startUptimeNs: Long = NOT_STARTED

        @MainThread
        fun onStart(message: String, nowNs: Long) {
            this.message = message
            startUptimeNs = nowNs
        }

        @MainThread
        @Suppress("ReturnCount")
        fun onFinish(nowNs: Long): LongTaskParameters? {
            if (startUptimeNs == NOT_STARTED) return null
            val durationNs = nowNs - startUptimeNs
            startUptimeNs = NOT_STARTED
            if (durationNs <= thresholdNS) return null

            return LongTaskParameters(durationNs, target = message.removePrefix(PREFIX_START))
        }

        data class LongTaskParameters(val durationNs: Long, val target: String)
    }

    /**
     * The main [Looper] holds a single message-logging [Printer], so all the strategies in the
     * process have to share one, which multiplexes to them. [printers] being empty is what tracks
     * whether that printer is installed: [addPrinter] and [removePrinter] are serialized so the
     * array and the [Looper] can never disagree.
     *
     * Registration is keyed on identity, not [Any.equals]: two strategies configured with the same
     * threshold are equal but belong to different SDK instances, and both must be notified.
     *
     * The array is swapped wholesale under the lock and read without one, so [println] neither
     * blocks the main thread nor allocates for a message the [Looper] is about to dispatch.
     */
    internal object CompositePrinter : Printer {

        @Volatile
        private var printers: Array<Printer> = emptyArray()

        internal val registeredPrinters: List<Printer>
            get() = printers.toList()

        @AnyThread
        fun addPrinter(printer: Printer) = synchronized(this) {
            if (printers.any { it === printer }) return
            printers += printer
            if (printers.size == 1) {
                Looper.getMainLooper().setMessageLogging(this)
            }
        }

        @AnyThread
        fun removePrinter(printer: Printer) = synchronized(this) {
            printers = printers.filter { it !== printer }.toTypedArray()
            if (printers.isEmpty()) {
                Looper.getMainLooper().setMessageLogging(null)
            }
        }

        @MainThread
        override fun println(x: String?) {
            val snapshot = printers
            for (i in snapshot.indices) snapshot[i].println(x)
        }
    }
}
