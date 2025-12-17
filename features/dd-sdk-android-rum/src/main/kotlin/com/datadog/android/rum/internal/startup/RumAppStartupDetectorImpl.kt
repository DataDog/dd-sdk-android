/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

internal class RumAppStartupDetectorImpl(
    private val application: Application,
    private val buildSdkVersionProvider: BuildSdkVersionProvider,
    private val appStartupTimeProviderNs: () -> Long,
    private val timeProviderNs: () -> Long,
    private val listener: RumAppStartupDetector.Listener
) : RumAppStartupDetector, Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess: Boolean = true

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.Q) {
            onBeforeActivityCreated(activity, savedInstanceState)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (buildSdkVersionProvider.version < Build.VERSION_CODES.Q) {
            onBeforeActivityCreated(activity, savedInstanceState)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        numberOfActivities--
        if (numberOfActivities == 0) {
            isChangingConfigurations = activity.isChangingConfigurations
        }
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    private fun onBeforeActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        numberOfActivities++
        val nowNs = timeProviderNs()

        if (numberOfActivities == 1 && !isChangingConfigurations) {
            val processStartTimeNs = appStartupTimeProviderNs()

            val gapNs = nowNs - processStartTimeNs
            val hasSavedInstanceStateBundle = savedInstanceState != null
            val weakActivity = WeakReference(activity)

            val scenario = if (isFirstActivityForProcess) {
                if (gapNs > START_GAP_THRESHOLD_NS) {
                    RumStartupScenario.WarmFirstActivity(
                        initialTimeNs = nowNs,
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activity = weakActivity,
                        appStartActivityOnCreateGapNs = gapNs
                    )
                } else {
                    RumStartupScenario.Cold(
                        initialTimeNs = processStartTimeNs,
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activity = weakActivity,
                        appStartActivityOnCreateGapNs = gapNs
                    )
                }
            } else {
                RumStartupScenario.WarmAfterActivityDestroyed(
                    initialTimeNs = nowNs,
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = weakActivity
                )
            }

            listener.onAppStartupDetected(scenario)
        }

        isFirstActivityForProcess = false
    }

    override fun destroy() {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    companion object {
        private val START_GAP_THRESHOLD_NS = 5.seconds.inWholeNanoseconds
    }
}
