package com.datadog.android.support.fragment

import android.app.Activity
import android.os.Build
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.ViewTrackingStrategy

/**
 * A [ViewTrackingStrategy] that will track [Fragment]s as RUM views.
 *
 * Each fragment's lifecycle will be monitored to start and stop RUM views when relevant.
 *
 * **Note**: This version of the [FragmentViewTrackingStrategy] is compatible with
 * the AppCompat Support Library.
 */
@Suppress("DEPRECATION")
class FragmentViewTrackingStrategy : ActivityLifecycleTrackingStrategy(), ViewTrackingStrategy {

    // region ActivityLifecycleTrackingStrategy

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

    // endregion
}
