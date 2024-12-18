/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.os.Bundle
import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.utils.resolveViewName
import com.datadog.android.rum.internal.utils.runIfValid
import com.datadog.android.rum.tracking.ComponentPredicate
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal open class AndroidXFragmentLifecycleCallbacks(
    internal val argumentsProvider: (Fragment) -> Map<String, Any?>,
    private val componentPredicate: ComponentPredicate<Fragment>,
    private val rumFeature: RumFeature,
    private val rumMonitor: RumMonitor
) : FragmentLifecycleCallbacks<FragmentActivity>, FragmentManager.FragmentLifecycleCallbacks() {

    protected lateinit var sdkCore: FeatureSdkCore
    private val executor: ScheduledExecutorService by lazy {
        sdkCore.createScheduledExecutorService(
            "rum-fragmentx-lifecycle"
        )
    }

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
    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
        super.onFragmentViewCreated(fm, f, v, savedInstanceState)
        startGesturesTracking(f)
    }

    private fun startGesturesTracking(f: Fragment) {
        val context = f.context ?: return
        if (f !is DialogFragment || !this::sdkCore.isInitialized) return

        rumFeature.actionTrackingStrategy.getGesturesTracker()
            .startTracking(f.dialog?.window, context, sdkCore)
    }

    @MainThread
    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        super.onFragmentResumed(fm, f)
        componentPredicate.runIfValid(f, internalLogger) {
            val key = resolveKey(it)
            val viewName = componentPredicate.resolveViewName(f)
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            rumMonitor.startView(key, viewName, argumentsProvider(it))
        }
    }

    @MainThread
    override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
        super.onFragmentStopped(fm, f)
        executor.scheduleSafe(
            "Delayed view stop",
            STOP_VIEW_DELAY_MS,
            TimeUnit.MILLISECONDS,
            sdkCore.internalLogger
        ) {
            componentPredicate.runIfValid(f, internalLogger) {
                val key = resolveKey(it)
                rumMonitor.stopView(key)
            }
        }
    }

    // endregion

    // region utils

    open fun resolveKey(fragment: Fragment): Any {
        return fragment
    }

    // endregion

    companion object {
        internal const val STOP_VIEW_DELAY_MS = 200L
    }
}
