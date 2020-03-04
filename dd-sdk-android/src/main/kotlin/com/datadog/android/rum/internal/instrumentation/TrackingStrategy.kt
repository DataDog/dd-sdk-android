package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.rum.GlobalRum

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
    internal object GesturesTrackingStrategy : TrackingStrategy()

    internal object ActivityTrackingStrategy : TrackingStrategy() {
        override fun onActivityResumed(activity: Activity) {
            super.onActivityResumed(activity)
            GlobalRum.monitor.startView(activity, activity.javaClass.canonicalName!!)
        }

        override fun onActivityPaused(activity: Activity) {
            super.onActivityPaused(activity)
            GlobalRum.monitor.stopView(activity)
        }
    }

    // TODO RUMM-271
    internal object FragmentsTrackingStrategy : TrackingStrategy()
}
