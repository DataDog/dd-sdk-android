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
) : RumFirstDrawTimeReporter.Handle,
    RumWindowCallbackListener,
    View.OnAttachStateChangeListener,
    ViewTreeObserver.OnDrawListener {

    private var isCancelled: Boolean = false

    private var onDrawInvoked = false

    init {
        val window = activity.window
        val decorView = window.peekDecorView()

        if (decorView == null) {
            windowCallbacksRegistry.addListener(activity, this)
        } else {
            onDecorViewReady(decorView)
        }
    }

    override fun unsubscribe() {
        if (isCancelled) {
            return
        }
        isCancelled = true

        windowCallbacksRegistry.removeListener(activity, this)

        val decorView = activity.window.peekDecorView()
        if (decorView != null) {
            decorView.removeOnAttachStateChangeListener(this)
            removeOnDrawListener(decorView)
        }
    }

    override fun onContentChanged() {
        windowCallbacksRegistry.removeListener(activity, this)

        val decorView = getDecorView()
        onDecorViewReady(decorView)
    }

    private fun reportFirstFrame() {
        if (isCancelled) {
            return
        }

        val nowNs = timeProviderNs()
        callback.onFirstFrameDrawn(nowNs)
    }

    private fun onDecorViewReady(decorView: View) {
        if (isCancelled) {
            return
        }

        if (decorView.isAttachedToWindow) {
            registerOnDrawListener(decorView)
        } else {
            decorView.addOnAttachStateChangeListener(this)
        }
    }

    private fun registerOnDrawListener(decorView: View) {
        if (isCancelled) {
            return
        }

        if (decorView.viewTreeObserver.isAlive) {
            try {
                decorView.viewTreeObserver.addOnDrawListener(this)
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

    override fun onViewAttachedToWindow(v: View) {
        val decorView = getDecorView()

        registerOnDrawListener(activity.window.decorView)
        decorView.removeOnAttachStateChangeListener(this)
    }

    override fun onViewDetachedFromWindow(v: View) {
    }

    override fun onDraw() {
        if (onDrawInvoked) {
            return
        }
        onDrawInvoked = true
        onFirstDraw()

        val decorView = getDecorView()
        handler.post {
            removeOnDrawListener(decorView)
        }
    }

    private fun getDecorView(): View {
        return activity.window.decorView
    }

    private fun removeOnDrawListener(decorView: View) {
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
