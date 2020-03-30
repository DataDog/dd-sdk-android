package com.datadog.android.support.fragment

import android.app.Activity
import android.os.Build
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.ViewTrackingStrategy
import com.datadog.android.support.fragment.internal.CompatFragmentLifecycleCallbacks
import com.datadog.android.support.fragment.internal.LifecycleCallbacks
import com.datadog.android.support.fragment.internal.NoOpLifecycleCallback
import com.datadog.android.support.fragment.internal.OreoFragmentLifecycleCallbacks

/**
 * A [ViewTrackingStrategy] that will track [Fragment]s as RUM views.
 *
 * Each fragment's lifecycle will be monitored to start and stop RUM views when relevant.
 *
 * **Note**: This version of the [FragmentViewTrackingStrategy] is compatible with
 * the AppCompat Support Library.
 * @param trackArguments whether we track Fragment arguments
 */
@Suppress("DEPRECATION")
class FragmentViewTrackingStrategy(private val trackArguments: Boolean) :
    ActivityLifecycleTrackingStrategy(), ViewTrackingStrategy {

    // region ActivityLifecycleTrackingStrategy

    private val compatLifecycleCallbacks: LifecycleCallbacks<FragmentActivity>
            by lazy {
                CompatFragmentLifecycleCallbacks {
                    if (trackArguments) convertToRumAttributes(it.arguments) else emptyMap()
                }
            }
    private val oreoLifecycleCallbacks: LifecycleCallbacks<Activity>
            by lazy {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    OreoFragmentLifecycleCallbacks {
                        if (trackArguments) convertToRumAttributes(it.arguments) else emptyMap()
                    }
                } else {
                    NoOpLifecycleCallback
                }
            }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
            compatLifecycleCallbacks.register(activity as FragmentActivity)
        } else {
            // old deprecated way
            oreoLifecycleCallbacks.register(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
            compatLifecycleCallbacks.unregister(activity as FragmentActivity)
        } else {
            // old deprecated way
            oreoLifecycleCallbacks.unregister(activity)
        }
    }

    // endregion
}
