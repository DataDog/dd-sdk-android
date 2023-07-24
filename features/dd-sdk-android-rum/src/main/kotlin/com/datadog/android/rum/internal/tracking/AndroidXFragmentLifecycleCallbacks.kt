/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.utils.resolveViewName
import com.datadog.android.rum.utils.runIfValid

internal open class AndroidXFragmentLifecycleCallbacks(
    internal val argumentsProvider: (Fragment) -> Map<String, Any?>,
    private val componentPredicate: ComponentPredicate<Fragment>,
    private val rumFeature: RumFeature,
    private val rumMonitor: RumMonitor
) : FragmentLifecycleCallbacks<FragmentActivity>, FragmentManager.FragmentLifecycleCallbacks() {

    protected lateinit var sdkCore: FeatureSdkCore

    private val internalLogger: InternalLogger
        get() = if (this::sdkCore.isInitialized) {
            sdkCore.internalLogger
        } else {
            InternalLogger.UNBOUND
        }

    // region FragmentLifecycleCallbacks

    override fun register(activity: FragmentActivity, sdkCore: SdkCore) {
        this.sdkCore = sdkCore as FeatureSdkCore
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(this, true)
    }

    override fun unregister(activity: FragmentActivity) {
        activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
    }

    // endregion

    // TODO: RUMM-0000 Update Androidx packages and handle deprecated APIs
    @Suppress("DEPRECATION")
    override fun onFragmentActivityCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?
    ) {
        super.onFragmentActivityCreated(fm, f, savedInstanceState)

        val context = f.context

        if (f is DialogFragment && context != null && this::sdkCore.isInitialized) {
            val window = f.dialog?.window
            val gesturesTracker = rumFeature.actionTrackingStrategy.getGesturesTracker()
            gesturesTracker.startTracking(window, context, sdkCore)
        }
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        componentPredicate.runIfValid(f, internalLogger) {
            val key = resolveKey(it)
            val viewName = componentPredicate.resolveViewName(f)
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            rumMonitor.startView(key, viewName, argumentsProvider(it))
        }
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        super.onFragmentPaused(fm, f)
        componentPredicate.runIfValid(f, internalLogger) {
            val key = resolveKey(it)
            rumMonitor.stopView(key)
        }
    }

    // endregion

    // region utils

    open fun resolveKey(fragment: Fragment): Any {
        return fragment
    }

    // endregion
}
