package com.datadog.android.instrumentation.gesture

import android.app.Activity

internal class DatadogGesturesTracker : GesturesTracker {

    override fun startTracking(activity: Activity) {
        // Even though the decorView is marked as NonNul I've seen cases where this was null.
        // Better to be safe here.
        @Suppress("SENSELESS_COMPARISON")
        if (activity.window == null || activity.window.decorView == null) {
            return
        }
        val currentCallback = activity.window.callback
        val toWrap = if (currentCallback != null) {
            currentCallback
        } else {
            NoOpWindowCallback()
        }
        activity.window.callback = WindowCallbackWrapper(toWrap)
    }

    override fun stopTracking(activity: Activity) {
        val currentCallback = activity.window.callback
        if (currentCallback is WindowCallbackWrapper) {
            if (currentCallback.wrappedCallback !is NoOpWindowCallback) {
                activity.window.callback = currentCallback.wrappedCallback
            } else {
                activity.window.callback = null
            }
        }
    }
}
