/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.timeseries.collector

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread

/**
 * Keeps at most one delayed [call][runDelayed] posted to the main-thread [handler].
 *
 * Both lifecycle events handled by [DefaultTimeseriesCollector] and callbacks posted here are
 * serialized by the main looper. Consequently, cancellation cannot race with callback execution:
 * [cancel] either removes a callback that has not started yet, or runs after that callback has
 * completed. This lets the collector debounce short stopped-to-started transitions, including
 * Activity recreation during configuration changes, without synchronization around the callback.
 *
 * [runDelayed] and [cancel] must be called on the main thread, and [handler] must be associated
 * with that same thread.
 */
internal class Debouncer(
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var launchedCall: Runnable? = null

    @MainThread
    fun runDelayed(delayMs: Long, call: () -> Unit) {
        if (launchedCall != null) return

        val runnable = object : Runnable {
            override fun run() {
                if (launchedCall !== this) return
                launchedCall = null
                call()
            }
        }

        launchedCall = runnable
        if (!handler.postDelayed(runnable, delayMs)) {
            launchedCall = null
        }
    }

    @MainThread
    fun cancel() {
        launchedCall?.let(handler::removeCallbacks)
        launchedCall = null
    }
}
