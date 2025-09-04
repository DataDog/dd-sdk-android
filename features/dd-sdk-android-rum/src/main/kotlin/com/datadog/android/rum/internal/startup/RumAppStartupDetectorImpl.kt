/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.datadog.android.internal.utils.DDCoreSubscription
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private val START_GAP_THRESHOLD = 5.seconds

internal class RumAppStartupDetectorImpl(
    private val context: Context,
    private val appStartupTimeProvider: () -> Long,
    private val processImportanceProvider: () -> Int,
    private val timeProviderNanos: () -> Long,
): RumAppStartupDetector, Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess = true

    private val subscription = DDCoreSubscription.Companion.create<RumAppStartupDetector.Listener>()

    init {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onBeforeActivityCreated(activity, savedInstanceState)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
        val now = timeProviderNanos()

        if (numberOfActivities == 1 && !isChangingConfigurations) {
            val processStartTime = appStartupTimeProvider()

            val processStartedInForeground = processImportanceProvider() == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

            val gap = (now - processStartTime).nanoseconds
            val hasSavedInstanceStateBundle = savedInstanceState != null

            val scenario = if (isFirstActivityForProcess) {
                if (!processStartedInForeground || gap > START_GAP_THRESHOLD) {
                    RumStartupScenario.WarmFirstActivity(
                        startTimeNanos = now,
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activityName = activity.extractName(),
                        activity = activity
                    )
                } else {
                    RumStartupScenario.Cold(
                        startTimeNanos = processStartTime,
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activityName = activity.extractName(),
                        activity = activity
                    )
                }
            } else {
                RumStartupScenario.WarmAfterActivityDestroyed(
                    startTimeNanos = now,
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activityName = activity.extractName(),
                    activity = activity
                )
            }

            subscription.notify { onAppStartupDetected(scenario) }
        }

        isFirstActivityForProcess = false
        isChangingConfigurations = false
    }

    override fun addListener(listener: RumAppStartupDetector.Listener) {
        subscription.addListener(listener)
    }

    override fun removeListener(listener: RumAppStartupDetector.Listener) {
        subscription.removeListener(listener)
    }

    override fun onStop() {
        (context.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(this)
    }
}

private fun Activity.extractName(): String {
    return javaClass.canonicalName ?: javaClass.name
}
