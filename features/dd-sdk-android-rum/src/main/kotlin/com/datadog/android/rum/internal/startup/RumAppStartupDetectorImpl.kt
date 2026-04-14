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
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManagerImpl.Companion.MAX_TTID_DURATION_NS
import com.datadog.android.rum.startup.AppStartupActivityPredicate
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.seconds

internal class RumAppStartupDetectorImpl(
    private val application: Application,
    private val buildSdkVersionProvider: BuildSdkVersionProvider,
    private val appStartupTimeProvider: () -> Time,
    private val timeProvider: () -> Time,
    private val listener: RumAppStartupDetector.Listener,
    private val appStartupActivityPredicate: AppStartupActivityPredicate,
    private val rumFirstDrawTimeReporter: RumFirstDrawTimeReporter
) : RumAppStartupDetector, Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess: Boolean = true
    private var pendingScenario: RumStartupScenario? = null

    @Suppress("UnsafeThirdPartyFunctionCall") // map is initialized empty
    private val trackedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val firstFrameHandles = WeakHashMap<Activity, RumFirstDrawTimeReporter.Handle>()

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
        trackedActivities.remove(activity)

        firstFrameHandles.remove(activity)?.unsubscribe()

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

    @Suppress("LongMethod")
    private fun onBeforeActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        numberOfActivities++
        val now = timeProvider()

        val shouldTrackStartup = appStartupActivityPredicate.shouldTrackStartup(activity)

        if (shouldTrackStartup) {
            trackedActivities.add(activity)
        }

        // Clear a stale pending scenario so a re-launch in the same process
        // is not blocked by an interstitial that never forwarded TTID.
        val stalePending = pendingScenario
        if (stalePending != null &&
            now.nanoTime - stalePending.initialTime.nanoTime > MAX_TTID_DURATION_NS
        ) {
            pendingScenario = null
        }

        val isFirstTrackedActivityWithNoPendingStartup =
            trackedActivities.size == 1 &&
                !isChangingConfigurations &&
                shouldTrackStartup &&
                pendingScenario == null

        if (isFirstTrackedActivityWithNoPendingStartup) {
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

            pendingScenario = scenario

            listener.onAppStartupDetected(scenario)

            subscribeToFirstFrameDrawn(
                scenario = scenario,
                activity = activity,
                wasForwarded = false
            )

            isFirstActivityForProcess = false
        }

        // If a pending scenario exists and this is a different qualifying activity,
        // notify the listener so it can subscribe to this activity's first frame too.
        val currentPendingScenario = pendingScenario
        if (currentPendingScenario != null && shouldTrackStartup &&
            currentPendingScenario.activity.get() !== activity
        ) {
            subscribeToFirstFrameDrawn(
                scenario = currentPendingScenario,
                activity = activity,
                wasForwarded = true
            )
        }
    }

    private fun subscribeToFirstFrameDrawn(
        scenario: RumStartupScenario,
        activity: Activity,
        wasForwarded: Boolean
    ) {
        val callback = object : RumFirstDrawTimeReporter.Callback {
            override fun onFirstFrameDrawn(timestampNs: Long) {
                firstFrameHandles.remove(activity)

                // Another activity may have already reported TTID
                if (pendingScenario !== scenario) return

                val durationNs = timestampNs - scenario.initialTime.nanoTime

                listener.onTTIDComputed(
                    scenario = scenario,
                    durationNs = durationNs,
                    wasForwarded = wasForwarded
                )

                pendingScenario = null
            }
        }

        firstFrameHandles[activity] = rumFirstDrawTimeReporter.subscribeToFirstFrameDrawn(
            activity = activity,
            callback = callback
        )
    }

    override fun destroy() {
        pendingScenario = null
        application.unregisterActivityLifecycleCallbacks(this)

        firstFrameHandles.forEach { (_, handle) -> handle.unsubscribe() }
        firstFrameHandles.clear()
    }

    companion object {
        private val START_GAP_THRESHOLD_NS = 10.seconds.inWholeNanoseconds
    }
}
