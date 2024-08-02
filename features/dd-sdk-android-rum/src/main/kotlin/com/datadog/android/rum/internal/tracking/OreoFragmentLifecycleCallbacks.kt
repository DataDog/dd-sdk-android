/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.app.DialogFragment
import android.app.Fragment
import android.app.FragmentManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.utils.resolveViewName
import com.datadog.android.rum.internal.utils.runIfValid
import com.datadog.android.rum.tracking.ComponentPredicate
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
internal class OreoFragmentLifecycleCallbacks(
    private val argumentsProvider: (Fragment) -> Map<String, Any?>,
    private val componentPredicate: ComponentPredicate<Fragment>,
    private val rumFeature: RumFeature,
    private val rumMonitor: RumMonitor,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : FragmentLifecycleCallbacks<Activity>, FragmentManager.FragmentLifecycleCallbacks() {

    private lateinit var sdkCore: FeatureSdkCore
    private val executor: ScheduledExecutorService by lazy {
        sdkCore.createScheduledExecutorService(
            "rum-fragment-lifecycle"
        )
    }

    private val internalLogger: InternalLogger
        get() = if (this::sdkCore.isInitialized) {
            sdkCore.internalLogger
        } else {
            InternalLogger.UNBOUND
        }

    // region FragmentLifecycleCallbacks

    override fun register(activity: Activity, sdkCore: SdkCore) {
        this.sdkCore = sdkCore as FeatureSdkCore
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.O) {
            activity.fragmentManager.registerFragmentLifecycleCallbacks(this, true)
        }
    }

    override fun unregister(activity: Activity) {
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.O) {
            activity.fragmentManager.unregisterFragmentLifecycleCallbacks(this)
        }
    }

    // endregion

    // region FragmentManager.FragmentLifecycleCallbacks

    @Deprecated("Deprecated in Java")
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

    @Deprecated("Deprecated in Java")
    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        if (isNotAViewFragment(f)) return

        componentPredicate.runIfValid(f, internalLogger) {
            val viewName = componentPredicate.resolveViewName(f)
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            rumMonitor.startView(it, viewName, argumentsProvider(it))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
        super.onFragmentStopped(fm, f)
        if (isNotAViewFragment(f)) return
        executor.scheduleSafe(
            "Delayed view stop",
            STOP_VIEW_DELAY_MS,
            TimeUnit.MILLISECONDS,
            sdkCore.internalLogger
        ) {
            componentPredicate.runIfValid(f, internalLogger) {
                rumMonitor.stopView(it)
            }
        }
    }

    // endregion

    // region Internal

    private fun isNotAViewFragment(fragment: Fragment): Boolean {
        return fragment::class.java.name == REPORT_FRAGMENT_NAME
    }

    private companion object {
        private const val REPORT_FRAGMENT_NAME = "androidx.lifecycle.ReportFragment"
        private const val STOP_VIEW_DELAY_MS = 200L
    }

    // endregion
}
