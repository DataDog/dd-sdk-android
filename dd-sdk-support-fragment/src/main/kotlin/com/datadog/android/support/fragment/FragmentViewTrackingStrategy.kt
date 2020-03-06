package com.datadog.android.support.fragment

import android.app.Activity
import android.os.Build
import android.support.v4.app.FragmentActivity
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.ViewTrackingStrategy

/**
 * The SDK will monitor the FragmentManager lifecycle events
 * and will automatically start and stop RUM View for each resumed/paused fragment.
 */
class FragmentViewTrackingStrategy : ActivityLifecycleTrackingStrategy(), ViewTrackingStrategy {

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
            (activity as FragmentActivity)
                .supportFragmentManager
                .registerFragmentLifecycleCallbacks(CompatFragmentLifecycleCallbacks, true)
        } else {
            // old deprecated way
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity
                    .fragmentManager
                    .registerFragmentLifecycleCallbacks(OreoFragmentLifecycleCallbacks, true)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
            (activity as FragmentActivity)
                .supportFragmentManager
                .unregisterFragmentLifecycleCallbacks(CompatFragmentLifecycleCallbacks)
        } else {
            // old deprecated way
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity
                    .fragmentManager
                    .unregisterFragmentLifecycleCallbacks(OreoFragmentLifecycleCallbacks)
            }
        }
    }
}
