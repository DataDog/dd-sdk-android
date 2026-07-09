/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

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
            registerDestroyCleanup(activity, listener)
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

    // WindowCallbacksRegistryImpl stores Activity→WindowCallback in a WeakHashMap, but
    // WindowCallback holds a strong reference back to the Activity via FixedWindowCallback.delegate
    // (the Activity is its own Window.Callback). This circular reference prevents GC.
    // When an Activity is destroyed before setContentView is called (e.g. an interstitial that
    // just calls startActivity + finish), the listener never fires via onContentChanged, so the
    // entry is never cleaned up. Registering a lifecycle callback to remove it on destroy breaks
    // the strong reference and lets GC collect the Activity.
    private fun registerDestroyCleanup(activity: Activity, listener: WindowCallbackListener) {
        val application = activity.application ?: return
        val weakActivity = WeakReference(activity)
        // WeakReference so this callback (held by Application) does not itself keep the
        // listener (and through it, the Activity) alive.
        val weakListener = WeakReference(listener)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(destroyed: Activity) {
                if (destroyed === weakActivity.get()) {
                    weakListener.get()?.let { l ->
                        windowCallbacksRegistry.removeListener(destroyed, l)
                    }
                    application.unregisterActivityLifecycleCallbacks(this)
                }
            }
        })
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
