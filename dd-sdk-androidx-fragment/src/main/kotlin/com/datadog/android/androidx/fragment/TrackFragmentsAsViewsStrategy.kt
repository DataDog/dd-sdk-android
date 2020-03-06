package com.datadog.android.androidx.fragment

import android.app.Activity
import android.os.Build
import androidx.fragment.app.FragmentActivity
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy

/**
 * The SDK will monitor the FragmentManager lifecycle events
 * and will automatically start and stop RUM View for each resumed/paused fragment.
 */
class TrackFragmentsAsViewsStrategy : ActivityLifecycleTrackingStrategy() {

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
                    .registerFragmentLifecycleCallbacks(DefaultFragmentLifecycleCallbacks, true)
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
                    .unregisterFragmentLifecycleCallbacks(DefaultFragmentLifecycleCallbacks)
            }
        }
    }
}
