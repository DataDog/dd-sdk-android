/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// These types are public only so that :features:dd-sdk-android-rum and
// :features:dd-sdk-android-rum-prelaunch can share them across module boundaries. They are not
// part of the SDK's public API and carry no KDoc for that reason.
@file:Suppress(
    "PackageNameVisibility",
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty"
)

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

/**
 * Default implementation of [RumFirstDrawTimeReporter].
 *
 * Hooks into the activity's window via [ViewTreeObserver.OnDrawListener] to capture the first
 * frame draw timestamp, handling both the case where the decor view is already attached and
 * the case where it becomes available later via [WindowCallbacksRegistry].
 */
@Suppress("UnsafeThirdPartyFunctionCall")
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
    ): RumFirstDrawTimeReporter.Handle {
        val handle = HandleImpl(activity, callback)
        handle.init()
        return handle
    }

    private inner class HandleImpl(
        private val activity: Activity,
        private val callback: RumFirstDrawTimeReporter.Callback
    ) : RumFirstDrawTimeReporter.Handle,
        WindowCallbackListener,
        View.OnAttachStateChangeListener,
        ViewTreeObserver.OnDrawListener {

        @Volatile
        private var isCancelled = false
        private var onDrawInvoked = false

        fun init() {
            val decorView = activity.window.peekDecorView()
            if (decorView == null) {
                windowCallbacksRegistry.addListener(activity, this)
            } else {
                onDecorViewReady(decorView)
            }
        }

        override fun unsubscribe() {
            if (isCancelled) return
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
            onDecorViewReady(activity.window.decorView)
        }

        override fun onViewAttachedToWindow(v: View) {
            registerOnDrawListener(activity.window.decorView)
            activity.window.decorView.removeOnAttachStateChangeListener(this)
        }

        override fun onViewDetachedFromWindow(v: View) {}

        override fun onDraw() {
            if (onDrawInvoked || isCancelled) return
            onDrawInvoked = true

            val nowNs = timeProviderNs()
            handler.sendMessageAtFrontOfQueue(
                Message.obtain(
                    handler,
                    Runnable {
                        if (!isCancelled) callback.onFirstFrameDrawn(nowNs)
                    }
                ).apply { isAsynchronous = true }
            )

            val currentDecorView = activity.window.decorView
            handler.post { removeOnDrawListener(currentDecorView) }
        }

        private fun onDecorViewReady(decorView: View) {
            if (isCancelled) return
            if (decorView.isAttachedToWindow) {
                registerOnDrawListener(decorView)
            } else {
                decorView.addOnAttachStateChangeListener(this)
            }
        }

        private fun registerOnDrawListener(decorView: View) {
            if (isCancelled) return
            if (decorView.viewTreeObserver.isAlive) {
                try {
                    decorView.viewTreeObserver.addOnDrawListener(this)
                } catch (e: IllegalStateException) {
                    warnLogger("RumFirstDrawTimeReporterImpl unable to add onDrawListener onto viewTreeObserver", e)
                }
            }
        }

        private fun removeOnDrawListener(decorView: View) {
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
