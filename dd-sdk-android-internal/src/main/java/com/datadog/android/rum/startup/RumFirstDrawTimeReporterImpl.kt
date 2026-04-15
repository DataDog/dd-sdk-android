/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

class RumFirstDrawTimeReporterImpl(
    private val timeProviderNs: () -> Long,
    private val windowCallbacksRegistry: WindowCallbacksRegistry,
    private val handler: Handler,
    private val logTag: String = "DD/AppLaunch",
    private val warnLogger: (message: String, throwable: Throwable) -> Unit = { message, throwable ->
        Log.w(logTag, message, throwable)
    }
) : RumFirstDrawTimeReporter {

    override fun subscribeToFirstFrameDrawn(
        activity: Activity,
        callback: RumFirstDrawTimeReporter.Callback
    ) {
        val window = activity.window
        val decorView = window.peekDecorView()

        if (decorView == null) {
            val listener = object : WindowCallbackListener {
                override fun onContentChanged() {
                    windowCallbacksRegistry.removeListener(activity, this)
                    onDecorViewReady(activity, callback)
                }
            }
            windowCallbacksRegistry.addListener(activity, listener)
        } else {
            onDecorViewReady(activity, callback)
        }
    }

    private fun onDecorViewReady(
        activity: Activity,
        callback: RumFirstDrawTimeReporter.Callback
    ) {
        val window = activity.window
        val decorView = window.decorView

        if (decorView.isAttachedToWindow) {
            registerOnDrawListener(
                decorView = decorView,
                callback = callback
            )
        } else {
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    registerOnDrawListener(
                        decorView = decorView,
                        callback = callback
                    )
                    decorView.removeOnAttachStateChangeListener(this)
                }

                override fun onViewDetachedFromWindow(v: View) {
                }
            }
            decorView.addOnAttachStateChangeListener(attachListener)
        }
    }

    private fun registerOnDrawListener(
        decorView: View,
        callback: RumFirstDrawTimeReporter.Callback
    ) {
        val listener = object : ViewTreeObserver.OnDrawListener {
            private var invoked = false

            override fun onDraw() {
                if (invoked) {
                    return
                }
                invoked = true
                onFirstDraw(callback)

                handler.post {
                    if (decorView.viewTreeObserver.isAlive) {
                        try {
                            decorView.viewTreeObserver.removeOnDrawListener(this)
                        } catch (e: IllegalStateException) {
                            warnLogger("RumTTIDReporterImpl unable to remove onDrawListener from viewTreeObserver", e)
                        }
                    }
                }
            }
        }

        if (decorView.viewTreeObserver.isAlive) {
            try {
                decorView.viewTreeObserver.addOnDrawListener(listener)
            } catch (e: IllegalStateException) {
                warnLogger("RumFirstDrawTimeReporterImpl unable to add onDrawListener onto viewTreeObserver", e)
            }
        }
    }

    private fun onFirstDraw(callback: RumFirstDrawTimeReporter.Callback) {
        val nowNs = timeProviderNs()

        val block = Runnable {
            callback.onFirstFrameDrawn(nowNs)
        }

        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler, block).apply {
                isAsynchronous = true
            }
        )
    }
}
