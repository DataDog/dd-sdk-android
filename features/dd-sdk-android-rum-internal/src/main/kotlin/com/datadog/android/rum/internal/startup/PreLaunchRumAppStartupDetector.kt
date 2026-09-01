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
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.internal.utils.guardedProcessStartNs
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.internal.domain.Time
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton that captures app launch timing before the RUM SDK is initialized.
 *
 * Designed for cross-platform scenarios (React Native, Flutter, MAUI) where [Rum.enable]
 * may be called after the first Activity has already drawn its first frame. By installing
 * this detector in a [android.content.ContentProvider] that runs before SDK initialization,
 * timing data is captured and buffered. When [attach] is called from [Rum.enable], buffered
 * events are drained to the real listener.
 *
 * **Threading**: [install] and all [RumAppStartupDetector.Listener] callbacks run on the main
 * thread. [attach] is dispatched to the main thread by [Rum.enable], so no synchronization
 * is needed.
 */
@Suppress("UnsafeThirdPartyFunctionCall")
object PreLaunchRumAppStartupDetector : RumAppStartupDetector.Listener {

    private sealed class Event {
        data class AppStartupDetected(val scenario: RumStartupScenario) : Event()
        data class TTIDComputed(
            val scenario: RumStartupScenario,
            val durationNs: Long,
            val wasForwarded: Boolean
        ) : Event()
    }

    private var detectorImpl: RumAppStartupDetector? = null
    private val pendingEvents = mutableListOf<Event>()
    private var attachedListener: RumAppStartupDetector.Listener? = null

    /** The activity associated with the most recent [onAppStartupDetected] call, if any. */
    var capturedActivity: Activity? = null
        private set

    /**
     * Predicate deciding whether an Activity qualifies as a startup Activity.
     *
     * Defaults to accepting every Activity, because the user's RUM configuration is unknown
     * before the SDK initializes. [attach] overrides it with the configured predicate, which
     * then applies to any Activity created after that point.
     */
    var activityPredicate: (Activity) -> Boolean = { true }

    /** `true` if [install] has been called. */
    val isInstalled: Boolean get() = detectorImpl != null

    /** `true` if there are buffered events not yet drained to a listener. */
    val hasPendingEvents: Boolean get() = pendingEvents.isNotEmpty()

    /**
     * Installs the detector into [application].
     *
     * Must be called as early as possible — from a [android.content.ContentProvider.onCreate]
     * registered before the RUM SDK initializes. Idempotent.
     *
     * @param application The application to register lifecycle callbacks on.
     */
    // PreferTimeProvider: this object runs before the SDK (and therefore any TimeProvider)
    // exists, so the raw platform clocks are the only source of truth available here.
    @Suppress("PreferTimeProvider")
    fun install(application: Application) {
        if (detectorImpl != null) {
            return
        }

        val appStartTimeNs = computeProcessStartNs()
        val timeProvider = DefaultTimeProvider()

        detectorImpl = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
            appStartupTime = { Time.fromNanoTime(appStartTimeNs, timeProvider) },
            currentTime = { Time.now(timeProvider) },
            listener = this,
            appStartupActivityPredicate = { activityPredicate(it) },
            rumFirstDrawTimeReporter = RumFirstDrawTimeReporterImpl(
                timeProviderNs = { System.nanoTime() },
                windowCallbacksRegistry = WindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper())
            )
        )
    }

    /**
     * Attaches [listener] and drains any buffered events to it.
     *
     * After this call, future [onAppStartupDetected] and [onTTIDComputed] callbacks from the
     * underlying detector are forwarded directly to [listener] without buffering.
     *
     * Must be called on the main thread.
     *
     * @param listener The listener to receive startup events.
     */
    fun attach(listener: RumAppStartupDetector.Listener) {
        attachedListener = listener
        val drained = pendingEvents.toList()
        pendingEvents.clear()
        for (event in drained) {
            when (event) {
                is Event.AppStartupDetected -> {
                    listener.onAppStartupDetected(event.scenario)
                }
                is Event.TTIDComputed -> {
                    listener.onTTIDComputed(event.scenario, event.durationNs, event.wasForwarded)
                }
            }
        }
    }

    /**
     * Detaches the current listener without tearing down the underlying detector.
     *
     * Called when the RUM feature stops so the feature's listener — which holds an SDK core
     * reference — is not retained by this process-scoped singleton. Events arriving after this
     * call are buffered again.
     */
    fun detach() {
        attachedListener = null
        activityPredicate = { true }
    }

    // region RumAppStartupDetector.Listener

    override fun onAppStartupDetected(scenario: RumStartupScenario) {
        capturedActivity = scenario.activity.get()
        val attached = attachedListener
        if (attached != null) {
            attached.onAppStartupDetected(scenario)
        } else {
            pendingEvents.add(Event.AppStartupDetected(scenario))
        }
    }

    override fun onTTIDComputed(scenario: RumStartupScenario, durationNs: Long, wasForwarded: Boolean) {
        val attached = attachedListener
        if (attached != null) {
            attached.onTTIDComputed(scenario, durationNs, wasForwarded)
        } else {
            pendingEvents.add(Event.TTIDComputed(scenario, durationNs, wasForwarded))
        }
    }

    // endregion

    // region Internal

    // NewApi: Process.getStartElapsedRealtime is guarded by the isAtLeastN check below.
    // PreferTimeProvider: see install() — no TimeProvider exists this early in the process.
    @Suppress("NewApi", "PreferTimeProvider")
    internal fun computeProcessStartNs(): Long {
        if (!BuildSdkVersionProvider.DEFAULT.isAtLeastN) {
            return DdRumContentProvider.createTimeNs
        }
        val nowNs = System.nanoTime()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val diffMs = nowElapsedMs - Process.getStartElapsedRealtime()
        val computed = nowNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        return guardedProcessStartNs(
            computed = computed,
            fallback = DdRumContentProvider.createTimeNs,
            thresholdNs = PROCESS_START_THRESHOLD_NS
        )
    }

    internal val PROCESS_START_THRESHOLD_NS = 10.seconds.inWholeNanoseconds

    // endregion
}
