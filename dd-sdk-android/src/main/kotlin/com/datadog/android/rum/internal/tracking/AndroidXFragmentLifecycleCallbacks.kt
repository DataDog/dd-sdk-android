/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.core.internal.utils.runIfValid
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ComponentPredicate

internal open class AndroidXFragmentLifecycleCallbacks(
    internal val argumentsProvider: (Fragment) -> Map<String, Any?>,
    private val componentPredicate: ComponentPredicate<Fragment>,
    internal var viewLoadingTimer: ViewLoadingTimer = ViewLoadingTimer(),
    private val rumFeature: RumFeature,
    private val rumMonitor: RumMonitor,
    private val advancedRumMonitor: AdvancedRumMonitor
) : FragmentLifecycleCallbacks<FragmentActivity>, FragmentManager.FragmentLifecycleCallbacks() {

    // region FragmentLifecycleCallbacks

    override fun register(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(this, true)
    }

    override fun unregister(activity: FragmentActivity) {
        activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
    }

    // endregion

    // region FragmentManager.FragmentLifecycleCallbacks

    override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
        super.onFragmentAttached(fm, f, context)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onCreated(resolveKey(it))
        }
    }

    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
        super.onFragmentStarted(fm, f)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onStartLoading(resolveKey(it))
        }
    }

    // TODO: RUMM-0000 Update Androidx packages and handle deprecated APIs
    @Suppress("DEPRECATION")
    override fun onFragmentActivityCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?
    ) {
        super.onFragmentActivityCreated(fm, f, savedInstanceState)

        val context = f.context

        if (f is DialogFragment && context != null) {
            val window = f.dialog?.window
            val gesturesTracker = rumFeature.actionTrackingStrategy.getGesturesTracker()
            gesturesTracker.startTracking(window, context)
        }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        componentPredicate.runIfValid(f) {
            val key = resolveKey(it)
            viewLoadingTimer.onFinishedLoading(key)
            val viewName = componentPredicate.resolveViewName(f)
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            rumMonitor.startView(key, viewName, argumentsProvider(it))
            val loadingTime = viewLoadingTimer.getLoadingTime(key)
            if (loadingTime != null) {
                advancedRumMonitor.updateViewLoadingTime(
                    key,
                    loadingTime,
                    resolveLoadingType(viewLoadingTimer.isFirstTimeLoading(key))
                )
            }
        }
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        componentPredicate.runIfValid(f) {
            val key = resolveKey(it)
            rumMonitor.stopView(key)
            viewLoadingTimer.onPaused(key)
        }
    }

    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
        super.onFragmentDestroyed(fm, f)
        componentPredicate.runIfValid(f) {
            viewLoadingTimer.onDestroyed(resolveKey(it))
        }
    }

    // endregion

    // region utils

    open fun resolveKey(fragment: Fragment): Any {
        return fragment
    }

    private fun resolveLoadingType(firstTimeLoading: Boolean): ViewEvent.LoadingType {
        return if (firstTimeLoading) {
            ViewEvent.LoadingType.FRAGMENT_DISPLAY
        } else {
            ViewEvent.LoadingType.FRAGMENT_REDISPLAY
        }
    }

    // endregion
}
