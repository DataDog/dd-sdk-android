package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.app.DialogFragment
import android.app.Fragment
import android.app.FragmentManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.core.internal.utils.runIfValid
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
internal class OreoFragmentLifecycleCallbacks(
    private val argumentsProvider: (Fragment) -> Map<String, Any?>,
    private val componentPredicate: ComponentPredicate<Fragment>,
    private val viewLoadingTimer: ViewLoadingTimer = ViewLoadingTimer(),
    private val rumMonitor: RumMonitor,
    private val advancedRumMonitor: AdvancedRumMonitor
) : FragmentLifecycleCallbacks<Activity>, FragmentManager.FragmentLifecycleCallbacks() {

    // region FragmentLifecycleCallbacks

    override fun register(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.fragmentManager.registerFragmentLifecycleCallbacks(this, true)
        }
    }

    override fun unregister(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.fragmentManager.unregisterFragmentLifecycleCallbacks(this)
        }
    }

    // endregion

    // region FragmentManager.FragmentLifecycleCallbacks

    override fun onFragmentActivityCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?
    ) {
        super.onFragmentActivityCreated(fm, f, savedInstanceState)

        val context = f.context

        if (f is DialogFragment && context != null) {
            val window = f.dialog?.window
            val gesturesTracker = RumFeature.gesturesTracker
            gesturesTracker.startTracking(window, context)
        }
    }

    override fun onFragmentAttached(fm: FragmentManager?, f: Fragment, context: Context?) {
        super.onFragmentAttached(fm, f, context)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onCreated(it)
        }
    }

    override fun onFragmentStarted(fm: FragmentManager?, f: Fragment) {
        super.onFragmentStarted(fm, f)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onStartLoading(it)
        }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onFinishedLoading(f)
            rumMonitor.startView(it, it.resolveViewName(), argumentsProvider(it))
            val loadingTime = viewLoadingTimer.getLoadingTime(it)
            if (loadingTime != null) {
                advancedRumMonitor.updateViewLoadingTime(
                    it,
                    loadingTime,
                    resolveLoadingType(viewLoadingTimer.isFirstTimeLoading(it))
                )
            }
        }
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        componentPredicate.runIfValid(f) {
            rumMonitor.stopView(it)
            viewLoadingTimer.onPaused(it)
        }
    }

    override fun onFragmentDestroyed(fm: FragmentManager?, f: Fragment) {
        super.onFragmentDestroyed(fm, f)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onDestroyed(it)
        }
    }

    // endregion

    // region Internal

    private fun resolveLoadingType(firstTimeLoading: Boolean): ViewEvent.LoadingType {
        return if (firstTimeLoading) {
            ViewEvent.LoadingType.FRAGMENT_DISPLAY
        } else {
            ViewEvent.LoadingType.FRAGMENT_REDISPLAY
        }
    }

    // endregion
}
