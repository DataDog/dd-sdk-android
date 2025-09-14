/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewTreeObserver
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.subscribeToFirstDrawFinished
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.nanoseconds

internal class RumTTIDReporter(
    private val internalLogger: InternalLogger,
) {
    private val handler = Handler(Looper.getMainLooper())

    private val windowCallbacksRegistry = RumTTIDReportedWindowCallbackRegistry()
    private val windowCallbackListeners = WeakHashMap<Activity, RumWindowCallbackListener>()

    private val onDrawListeners = WeakHashMap<Activity, ViewTreeObserver.OnDrawListener>()

    fun onAppStartupDetected(scenario: RumStartupScenario) {
        val listener = object : RumWindowCallbackListener {
            override fun onContentChanged() {
                windowCallbackListeners.remove(scenario.activity)?.let {
                    windowCallbacksRegistry.removeListener(scenario.activity, it)
                }
                onDecorViewReady(scenario)
            }
        }
        windowCallbacksRegistry.addListener(scenario.activity, listener)
        windowCallbackListeners.put(scenario.activity, listener)
    }

    private fun onDecorViewReady(scenario: RumStartupScenario) {
        val decorView = scenario.activity.window.decorView

        val listener = object : ViewTreeObserver.OnDrawListener {
            private var invoked = false

            override fun onDraw() {
                if (invoked) {
                    return
                }
                invoked = true
                onFirstDraw(scenario)

                handler.post {
                    onDrawListeners.remove(scenario.activity)

                    if (decorView.viewTreeObserver.isAlive) {
                        decorView.viewTreeObserver.removeOnDrawListener(this)
                    }
                }
            }
        }

        if (decorView.viewTreeObserver.isAlive) {
            decorView.viewTreeObserver.addOnDrawListener(listener)
            onDrawListeners.put(scenario.activity, listener)
        }
    }

    private fun onFirstDraw(scenario: RumStartupScenario) {
        val duration = (System.nanoTime() - scenario.initialTimeNanos).nanoseconds

        val block = Runnable {
            Log.w("WAHAHA", "onFirstDraw ${scenario.name()} $duration")
        }

        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler, block).apply {
                isAsynchronous = true
            }
        )
    }
}
