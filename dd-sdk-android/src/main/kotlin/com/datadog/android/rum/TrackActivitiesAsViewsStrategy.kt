package com.datadog.android.rum

import android.app.Activity

/**
 * The SDK will monitor the Activity lifecycle events
 * and will automatically start and stop RUM View for each resumed/paused activity.
 */
class TrackActivitiesAsViewsStrategy : ActivityLifecycleTrackingStrategy() {
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        GlobalRum.monitor.startView(activity, activity.javaClass.canonicalName!!)
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        GlobalRum.monitor.stopView(activity)
    }
}
