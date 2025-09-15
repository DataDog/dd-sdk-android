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
import android.view.ViewTreeObserver
import com.datadog.android.rum.internal.utils.RumWindowCallbacksRegistry
import com.datadog.android.rum.internal.utils.RumWindowCallbacksRegistryImpl
import com.datadog.android.rum.internal.utils.RumWindowCallbackListener
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.nanoseconds

internal interface RumTTIDReporterListener {
    fun onTTID(scenario: RumStartupScenario, timestampNanos: Long)
}

internal interface RumTTIDReporter {
    fun onAppStartupDetected(scenario: RumStartupScenario)

    companion object {
        fun create(listener: RumTTIDReporterListener): RumTTIDReporter {
            return RumTTIDReporterImpl(
                timeProviderNanos = { System.nanoTime() },
                windowCallbacksRegistry = RumWindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper()),
                listener = listener
            )
        }
    }
}

internal class RumTTIDReporterImpl(
    private val timeProviderNanos: () -> Long,
    private val windowCallbacksRegistry: RumWindowCallbacksRegistry,
    private val handler: Handler,
    private val listener: RumTTIDReporterListener,
): RumTTIDReporter {
    private val windowCallbackListeners = WeakHashMap<Activity, RumWindowCallbackListener>()

    private val onDrawListeners = WeakHashMap<Activity, ViewTreeObserver.OnDrawListener>()

    override fun onAppStartupDetected(scenario: RumStartupScenario) {
        val activity = scenario.activity
        val window = activity.window
        val decorView = window.peekDecorView()

        if (decorView == null) {
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
        } else {
            onDecorViewReady(scenario)
        }
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
        val duration = (timeProviderNanos() - scenario.initialTimeNanos).nanoseconds

        val block = Runnable {
            listener.onTTID(scenario, duration.inWholeNanoseconds)
        }

        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler, block).apply {
                isAsynchronous = true
            }
        )
    }
}
