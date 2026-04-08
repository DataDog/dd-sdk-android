/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.utils.window.RumWindowCallbackListener
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistry

internal class RumFirstDrawTimeReporterHandleImpl(
    private val callback: RumFirstDrawTimeReporter.Callback,
    private val activity: Activity,
    private val internalLogger: InternalLogger,
    private val timeProviderNs: () -> Long,
    private val windowCallbacksRegistry: RumWindowCallbacksRegistry,
    private val handler: Handler
) : RumFirstDrawTimeReporter.Handle, RumWindowCallbackListener {

    private var isCancelled: Boolean = false

    init {
        val window = activity.window
        val decorView = window.peekDecorView()

        if (decorView == null) {
            windowCallbacksRegistry.addListener(activity, this)
        } else {
            onDecorViewReady()
        }
    }

    override fun unsubscribe() {
        if (isCancelled) {
            return
        }
        isCancelled = true

        windowCallbacksRegistry.removeListener(activity, this)
    }

    override fun onContentChanged() {
        windowCallbacksRegistry.removeListener(activity, this)
        onDecorViewReady()
    }

    private fun reportFirstFrame() {
        if (isCancelled) {
            return
        }

        val nowNs = timeProviderNs()
        callback.onFirstFrameDrawn(nowNs)
    }

    private fun onDecorViewReady() {
        if (isCancelled) {
            return
        }

        val window = activity.window
        val decorView = window.decorView

        if (decorView.isAttachedToWindow) {
            registerOnDrawListener(decorView)
        } else {
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    registerOnDrawListener(decorView)
                    decorView.removeOnAttachStateChangeListener(this)
                }

                override fun onViewDetachedFromWindow(v: View) {
                }
            }
            decorView.addOnAttachStateChangeListener(attachListener)
        }
    }

    private fun registerOnDrawListener(decorView: View) {
        if (isCancelled) {
            return
        }

        val listener = object : ViewTreeObserver.OnDrawListener {
            private var invoked = false

            override fun onDraw() {
                if (invoked) {
                    return
                }
                invoked = true
                onFirstDraw()

                handler.post {
                    if (decorView.viewTreeObserver.isAlive) {
                        try {
                            decorView.viewTreeObserver.removeOnDrawListener(this)
                        } catch (e: IllegalStateException) {
                            internalLogger.log(
                                InternalLogger.Level.WARN,
                                InternalLogger.Target.TELEMETRY,
                                { "RumTTIDReporterImpl unable to remove onDrawListener from viewTreeObserver" },
                                e
                            )
                        }
                    }
                }
            }
        }

        if (decorView.viewTreeObserver.isAlive) {
            try {
                decorView.viewTreeObserver.addOnDrawListener(listener)
            } catch (e: IllegalStateException) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { "RumFirstDrawTimeReporterImpl unable to add onDrawListener onto viewTreeObserver" },
                    e
                )
            }
        }
    }

    private fun onFirstDraw() {
        val block = Runnable {
            reportFirstFrame()
        }

        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler, block).apply {
                isAsynchronous = true
            }
        )
    }
}
