package com.datadog.android.instrumentation.gesture

import android.app.Activity

internal class NoOpGesturesTracker : GesturesTracker {

    override fun startTracking(activity: Activity) {
        // No Op
    }

    override fun stopTracking(activity: Activity) {
        // No Op
    }
}
