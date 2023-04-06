/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.content.Context
import android.os.Looper
import android.util.Printer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.v2.api.SdkCore
import java.util.concurrent.TimeUnit

internal class MainLooperLongTaskStrategy(
    internal val thresholdMs: Long
) : Printer, TrackingStrategy {

    private val thresholdNS = TimeUnit.MILLISECONDS.toNanos(thresholdMs)
    private var startUptimeNs: Long = 0L
    private var target: String = ""
    private lateinit var sdkCore: SdkCore

    // region TrackingStrategy

    override fun register(sdkCore: SdkCore, context: Context) {
        this.sdkCore = sdkCore
        Looper.getMainLooper().setMessageLogging(this)
    }

    override fun unregister(context: Context?) {
        Looper.getMainLooper().setMessageLogging(null)
    }

    // endregion

    // region Printer

    override fun println(x: String?) {
        if (x != null) {
            detectLongTask(x)
        }
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MainLooperLongTaskStrategy

        if (thresholdMs != other.thresholdMs) return false

        return true
    }

    override fun hashCode(): Int {
        return thresholdMs.hashCode()
    }

    override fun toString(): String {
        return "MainLooperLongTaskStrategy($thresholdMs)"
    }

    // endregion

    // region Internal

    private fun detectLongTask(message: String) {
        val now = System.nanoTime()
        if (message.startsWith(PREFIX_START)) {
            @Suppress("UnsafeThirdPartyFunctionCall") // substring can't throw IndexOutOfBounds
            target = message.substring(PREFIX_START_LENGTH)
            startUptimeNs = now
        } else if (message.startsWith(PREFIX_END)) {
            val durationNs = now - startUptimeNs
            if (durationNs > thresholdNS && this::sdkCore.isInitialized) {
                (GlobalRum.get(sdkCore) as? AdvancedRumMonitor)?.addLongTask(durationNs, target)
            }
        }
    }

    // endregion

    companion object {
        private const val PREFIX_START = ">>>>> Dispatching to "
        private const val PREFIX_END = "<<<<< Finished to "
        private const val PREFIX_START_LENGTH = PREFIX_START.length
    }
}
