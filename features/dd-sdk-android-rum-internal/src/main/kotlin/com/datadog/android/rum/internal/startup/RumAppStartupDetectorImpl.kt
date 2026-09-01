/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// These types are public only so that :features:dd-sdk-android-rum and
// :features:dd-sdk-android-rum-prelaunch can share them across module boundaries. They are not
// part of the SDK's public API and carry no KDoc for that reason.
@file:Suppress(
    "PackageNameVisibility",
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty"
)

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.Time
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.minutes

class RumAppStartupDetectorImpl(
    private val application: Application,
    private val buildSdkVersionProvider: BuildSdkVersionProvider,
    private val appStartupTime: () -> Time,
    private val currentTime: () -> Time,
    private val listener: RumAppStartupDetector.Listener,
    private val appStartupActivityPredicate: (Activity) -> Boolean,
    private val rumFirstDrawTimeReporter: RumFirstDrawTimeReporter
) : RumAppStartupDetector, Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess: Boolean = true
    private var pendingScenario: RumStartupScenario? = null

    @Suppress("UnsafeThirdPartyFunctionCall") // map is initialized empty
    private val trackedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    @Suppress("UnsafeThirdPartyFunctionCall") // map is initialized empty
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

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    private fun onBeforeActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        numberOfActivities++
        val now = currentTime()

        val shouldTrackStartup = appStartupActivityPredicate(activity)

        if (shouldTrackStartup) {
            trackedActivities.add(activity)
        }

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
            val processStartTime = appStartupTime()
            val scenario = RumStartupScenario.build(
                isFirstActivityForProcess = isFirstActivityForProcess,
                hasSavedInstanceStateBundle = savedInstanceState != null,
                activity = WeakReference(activity),
                processStartTime = processStartTime,
                activityOnCreateTime = now
            )

            pendingScenario = scenario
            listener.onAppStartupDetected(scenario)
            subscribeToFirstFrameDrawn(scenario, activity, wasForwarded = false)
            isFirstActivityForProcess = false
        }

        val currentPendingScenario = pendingScenario
        if (currentPendingScenario != null && shouldTrackStartup &&
            currentPendingScenario.activity.get() !== activity
        ) {
            subscribeToFirstFrameDrawn(currentPendingScenario, activity, wasForwarded = true)
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
        private val MAX_TTID_DURATION_NS = 1.minutes.inWholeNanoseconds
    }
}
