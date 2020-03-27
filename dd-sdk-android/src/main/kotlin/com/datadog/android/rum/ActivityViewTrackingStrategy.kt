package com.datadog.android.rum

import android.app.Activity

/**
 * A [ViewTrackingStrategy] that will track [Activity] as RUM views.
 *
 * Each activity's lifecycle will be monitored to start and stop RUM views when relevant.
 */
class ActivityViewTrackingStrategy : ActivityLifecycleTrackingStrategy(), ViewTrackingStrategy {

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        val javaClass = activity.javaClass
        GlobalRum.monitor.startView(activity, javaClass.canonicalName ?: javaClass.simpleName)
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        GlobalRum.monitor.stopView(activity)
    }

    // endregion
}
