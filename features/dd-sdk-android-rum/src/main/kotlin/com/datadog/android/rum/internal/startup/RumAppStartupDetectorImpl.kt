/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.startup.AppStartupActivityPredicate
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

internal class RumAppStartupDetectorImpl(
    private val application: Application,
    private val buildSdkVersionProvider: BuildSdkVersionProvider,
    private val appStartupTimeProvider: () -> Time,
    private val timeProvider: () -> Time,
    private val listener: RumAppStartupDetector.Listener,
    private val appStartupActivityPredicate: AppStartupActivityPredicate
) : RumAppStartupDetector, Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess: Boolean = true
    private var pendingStartupScenario: RumStartupScenario? = null
    private var startupTTIDReported: Boolean = false

    private val trackedActivities = mutableListOf<WeakReference<Activity>>()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (buildSdkVersionProvider.isAtLeastQ) {
            onBeforeActivityCreated(activity, savedInstanceState)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!buildSdkVersionProvider.isAtLeastQ) {
            onBeforeActivityCreated(activity, savedInstanceState)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        numberOfActivities--
        trackedActivities.removeAll { it.get() === activity || it.get() == null }

        val pending = pendingStartupScenario
        if (pending != null && isStartupActivityDestroyedBeforeFirstDraw(pending, activity)) {
            val nextActivity = trackedActivities.firstOrNull { it.get() != null }?.get()
            if (nextActivity != null) {
                val forwarded = pending.forwardTo(WeakReference(nextActivity))
                pendingStartupScenario = forwarded
                listener.onAppStartupRetargeted(forwarded)
            } else {
                pendingStartupScenario = null
            }
        }

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

    private fun isStartupActivityDestroyedBeforeFirstDraw(
        pending: RumStartupScenario,
        activity: Activity
    ): Boolean {
        return !startupTTIDReported &&
            pending.activity.get() === activity &&
            !activity.isChangingConfigurations
    }

    private fun onBeforeActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        numberOfActivities++
        val now = timeProvider()

        val shouldTrackStartup = appStartupActivityPredicate.shouldTrackStartup(activity)

        if (shouldTrackStartup) {
            trackedActivities.add(WeakReference(activity))
        }

        val isFirstTrackedActivity = shouldTrackStartup && trackedActivities.count { it.get() != null } == 1
        if (isFirstTrackedActivity && !isChangingConfigurations) {
            val processStartTime = appStartupTimeProvider()

            val gapNs = now.nanoTime - processStartTime.nanoTime
            val hasSavedInstanceStateBundle = savedInstanceState != null
            val weakActivity = WeakReference(activity)

            val scenario = if (isFirstActivityForProcess) {
                if (gapNs > START_GAP_THRESHOLD_NS) {
                    RumStartupScenario.WarmFirstActivity(
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activity = weakActivity,
                        appStartActivityOnCreateGapNs = gapNs,
                        initialTime = now
                    )
                } else {
                    RumStartupScenario.Cold(
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activity = weakActivity,
                        appStartActivityOnCreateGapNs = gapNs,
                        initialTime = processStartTime
                    )
                }
            } else {
                RumStartupScenario.WarmAfterActivityDestroyed(
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = weakActivity,
                    initialTime = now
                )
            }

            pendingStartupScenario = scenario
            startupTTIDReported = false
            listener.onAppStartupDetected(scenario)
            isFirstActivityForProcess = false
        }
    }

    override fun notifyStartupTTIDReported() {
        startupTTIDReported = true
        pendingStartupScenario = null
    }

    override fun destroy() {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    companion object {
        private val START_GAP_THRESHOLD_NS = 10.seconds.inWholeNanoseconds
    }
}
