package com.datadog.android.androidx.fragment

import android.app.Activity
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.datadog.android.androidx.fragment.internal.CompatFragmentLifecycleCallbacks
import com.datadog.android.androidx.fragment.internal.LifecycleCallbacks
import com.datadog.android.androidx.fragment.internal.NoOpLifecycleCallback
import com.datadog.android.androidx.fragment.internal.OreoFragmentLifecycleCallbacks
import com.datadog.android.rum.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.ViewTrackingStrategy

/**
 * A [ViewTrackingStrategy] that will track [Fragment]s as RUM views.
 *
 * Each fragment's lifecycle will be monitored to start and stop RUM views when relevant.
 *
 * **Note**: This version of the [FragmentViewTrackingStrategy] is compatible with
 * the AndroidX Compat Library.
 * @param trackArguments whether we track Fragment arguments
 */
class FragmentViewTrackingStrategy(private val trackArguments: Boolean) :
    ActivityLifecycleTrackingStrategy(), ViewTrackingStrategy {

    // region ActivityLifecycleTrackingStrategy

    private val compatLifecycleCallbacks: LifecycleCallbacks<FragmentActivity>
            by lazy {
                CompatFragmentLifecycleCallbacks {
                    if (trackArguments) asRumAttributes(it.arguments) else emptyMap()
                }
            }
    private val oreoLifecycleCallbacks: LifecycleCallbacks<Activity>
            by lazy {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    OreoFragmentLifecycleCallbacks {
                        if (trackArguments) asRumAttributes(it.arguments) else emptyMap()
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
