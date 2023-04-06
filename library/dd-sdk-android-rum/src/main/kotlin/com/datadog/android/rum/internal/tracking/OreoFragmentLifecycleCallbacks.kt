@file:Suppress("DEPRECATION")

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.app.DialogFragment
import android.app.Fragment
import android.app.FragmentManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.system.DefaultBuildSdkVersionProvider
import com.datadog.android.rum.RumFeature
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.utils.resolveViewName
import com.datadog.android.rum.utils.runIfValid
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
internal class OreoFragmentLifecycleCallbacks(
    private val argumentsProvider: (Fragment) -> Map<String, Any?>,
    private val componentPredicate: ComponentPredicate<Fragment>,
    private val viewLoadingTimer: ViewLoadingTimer = ViewLoadingTimer(),
    private val rumFeature: RumFeature,
    private val rumMonitor: RumMonitor,
    private val advancedRumMonitor: AdvancedRumMonitor,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider()
) : FragmentLifecycleCallbacks<Activity>, FragmentManager.FragmentLifecycleCallbacks() {

    private lateinit var sdkCore: SdkCore

    private val internalLogger: InternalLogger
        get() = if (this::sdkCore.isInitialized) {
            sdkCore._internalLogger
        } else {
            InternalLogger.UNBOUND
        }

    // region FragmentLifecycleCallbacks

    override fun register(activity: Activity, sdkCore: SdkCore) {
        this.sdkCore = sdkCore
        if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.O) {
            activity.fragmentManager.registerFragmentLifecycleCallbacks(this, true)
        }
    }

    override fun unregister(activity: Activity) {
        if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.O) {
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
        if (isNotAViewFragment(f)) return

        val context = f.context
        if (f is DialogFragment && context != null && this::sdkCore.isInitialized) {
            val window = f.dialog?.window
            val gesturesTracker = rumFeature.actionTrackingStrategy.getGesturesTracker()
            gesturesTracker.startTracking(window, context, sdkCore)
        }
    }

    override fun onFragmentAttached(fm: FragmentManager?, f: Fragment, context: Context?) {
        super.onFragmentAttached(fm, f, context)
        if (isNotAViewFragment(f)) return
        componentPredicate.runIfValid(f, internalLogger) {
            viewLoadingTimer.onCreated(it)
        }
    }

    override fun onFragmentStarted(fm: FragmentManager?, f: Fragment) {
        super.onFragmentStarted(fm, f)
        if (isNotAViewFragment(f)) return
        componentPredicate.runIfValid(f, internalLogger) {
            viewLoadingTimer.onStartLoading(it)
        }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        if (isNotAViewFragment(f)) return

        componentPredicate.runIfValid(f, internalLogger) {
            val viewName = componentPredicate.resolveViewName(f)
            viewLoadingTimer.onFinishedLoading(f)
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            rumMonitor.startView(it, viewName, argumentsProvider(it))
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
        if (isNotAViewFragment(f)) return

        componentPredicate.runIfValid(f, internalLogger) {
            rumMonitor.stopView(it)
            viewLoadingTimer.onPaused(it)
        }
    }

    override fun onFragmentDestroyed(fm: FragmentManager?, f: Fragment) {
        super.onFragmentDestroyed(fm, f)
        if (isNotAViewFragment(f)) return

        componentPredicate.runIfValid(f, internalLogger) {
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

    private fun isNotAViewFragment(fragment: Fragment): Boolean {
        return fragment::class.java.name == REPORT_FRAGMENT_NAME
    }

    private companion object {
        private const val REPORT_FRAGMENT_NAME = "androidx.lifecycle.ReportFragment"
    }

    // endregion
}
