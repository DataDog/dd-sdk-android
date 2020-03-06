package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.UserActionTrackingStrategy
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker

internal class GesturesTrackingStrategy(private val gesturesTracker: GesturesTracker) :
    ActivityLifecycleTrackingStrategy(), UserActionTrackingStrategy {
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        gesturesTracker.startTracking(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        gesturesTracker.stopTracking(activity)
    }
}
