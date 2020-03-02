package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal sealed class TrackingStrategy : Application.ActivityLifecycleCallbacks {

    override fun onActivityPaused(activity: Activity) {
        // No Op
    }

    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No Op
    }

    override fun onActivityResumed(activity: Activity) {
        // No Op
    }

    // TODO RUMM-242
    internal class GesturesTrackingStrategy : TrackingStrategy()

    internal class ActivityTrackingStrategy : TrackingStrategy()

    // TODO RUMM-271
    internal class FragmentsTrackingStrategy : TrackingStrategy()
}
