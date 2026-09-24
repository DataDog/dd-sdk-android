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

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.DefaultAppStartTimeProvider
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistryImpl

/**
 * Singleton that captures app launch timing before the RUM SDK is initialized.
 *
 * Designed for cross-platform scenarios (React Native, Flutter, MAUI) where [Rum.enable]
 * may be called after the first Activity has already drawn its first frame. By installing
 * this detector in a [android.content.ContentProvider] that runs before SDK initialization,
 * timing data is captured and buffered. When [attach] is called from [Rum.enable], buffered
 * events are replayed to the real listener.
 *
 * Being process-scoped, it serves every SDK core that enables RUM in the process: listeners
 * accumulate rather than replace each other.
 *
 * **Threading**: [install] and all [RumAppStartupDetector.Listener] callbacks run on the main
 * thread, and [attach]/[detach] are dispatched there by [Rum.enable], so the listener and event
 * state needs no synchronization. [isInstalled] is the exception: [Rum.enable] reads it from
 * whichever thread it was called on — a background thread for React Native and Flutter — before
 * any main-thread hop, so the field behind it is volatile.
 */
object PreLaunchRumAppStartupDetector : RumAppStartupDetector.Listener {

    private sealed class Event {
        abstract val scenario: RumStartupScenario

        data class AppStartupDetected(override val scenario: RumStartupScenario) : Event()
        data class TTIDComputed(
            override val scenario: RumStartupScenario,
            val durationNs: Long,
            val wasForwarded: Boolean
        ) : Event()
    }

    private var detectorImpl: RumAppStartupDetector? = null
    private val listeners = mutableListOf<RumAppStartupDetector.Listener>()

    /**
     * The events of the most recent launch, replayed to every listener that attaches.
     *
     * A launch is a unit — an AppStart followed (usually) by its TTID — and a listener joining
     * part-way through needs the whole of it, whether it attached before the SDK existed or in the
     * middle of a launch that is still in flight. A new AppStart supersedes the previous launch,
     * so this holds one launch at a time; it is dropped when the last listener detaches.
     */
    private val pendingEvents = mutableListOf<Event>()

    /** `true` if [install] has been called. */
    val isInstalled: Boolean get() = detectorImpl != null

    /** Number of listeners currently attached. Must be read on the main thread. */
    val attachedListenerCount: Int get() = listeners.size

    /**
     * Installs the detector into [application].
     *
     * Must be called as early as possible — from a [android.content.ContentProvider.onCreate]
     * registered before the RUM SDK initializes. Idempotent.
     *
     * @param application The application to register lifecycle callbacks on.
     */
    fun install(application: Application) {
        if (detectorImpl != null) {
            return
        }

        val timeProvider = DefaultTimeProvider()
        val appStartTimeNs = DefaultAppStartTimeProvider(
            timeProviderFactory = { timeProvider }
        ).appStartTimeNs

        detectorImpl = RumAppStartupDetectorImpl(
            application = application,
            buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
            appStartupTime = { Time.fromNanoTime(appStartTimeNs, timeProvider) },
            currentTime = { Time.now(timeProvider) },
            listener = this,
            // Every Activity is accepted. AppStartupActivityPredicate is documented as being
            // evaluated during Activity creation, which is what lets it move the measurement on to
            // the next Activity; a core enabling RUM after the launch was captured is too late for
            // that, and filtering at attach() time could only suppress the launch, never re-target
            // it. Honouring a predicate here means taking it at install() time, from the
            // ContentProvider that installs this detector.
            appStartupActivityPredicate = { true },
            rumFirstDrawTimeReporter = RumFirstDrawTimeReporterImpl(
                internalLogger = InternalLogger.UNBOUND,
                timeProviderNs = timeProvider::getDeviceElapsedTimeNanos,
                windowCallbacksRegistry = RumWindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper())
            )
        )
    }

    /**
     * Attaches [listener] and replays the most recent launch to it.
     *
     * Several SDK cores may enable RUM in the same process, so listeners accumulate rather than
     * replace each other. [pendingEvents] is *replayed*, not consumed, so a listener attaching
     * after the SDK-less capture — or in the middle of a launch another core is already hearing
     * about live — still sees the whole launch.
     *
     * Must be called on the main thread.
     *
     * @param listener The listener to receive startup events.
     */
    fun attach(listener: RumAppStartupDetector.Listener) {
        listeners.add(listener)
        pendingEvents.toList().forEach { forward(listener, it) }
    }

    /**
     * Detaches [listener] without tearing down the underlying detector.
     *
     * Called when a RUM feature stops so its listener — which holds that feature's SDK core — is
     * not retained by this process-scoped singleton. Only that one listener is removed; any other
     * core's listener keeps receiving events.
     *
     * When the last listener goes, a delivered launch is dropped from the buffer and events
     * arriving afterwards are buffered again for the next core to attach. A launch still awaiting
     * its first frame is kept instead: the detector is process-scoped and is not torn down here, so
     * it still holds the matching pending scenario and will emit the TTID, and dropping the
     * AppStart now would leave the next core to enable RUM replaying a TTID with no AppStart to be
     * indexed against.
     *
     * Must be called on the main thread.
     *
     * @param listener The listener to remove.
     */
    fun detach(listener: RumAppStartupDetector.Listener) {
        listeners.removeAll { it === listener }
        // The buffer holds one launch at a time, so the presence of a TTID means this one is
        // complete and every attached listener has already had the whole of it.
        val isLaunchInFlight = pendingEvents.isNotEmpty() &&
            pendingEvents.none { it is Event.TTIDComputed }
        if (listeners.isEmpty() && !isLaunchInFlight) {
            pendingEvents.clear()
        }
    }

    /**
     * Forwards [event] to [listener].
     *
     * A listener never sees a TTID without the AppStart it belongs to: [pendingEvents] holds the
     * whole of the launch in flight and [attach] replays it in order, so the pairing holds for a
     * listener that joins part-way through as much as for one that was there from the start. That
     * matters because consumers index a TTID against its launch, and one arriving on its own lands
     * on a negative index.
     */
    private fun forward(listener: RumAppStartupDetector.Listener, event: Event) {
        when (event) {
            is Event.AppStartupDetected -> listener.onAppStartupDetected(event.scenario)
            is Event.TTIDComputed -> listener.onTTIDComputed(
                event.scenario,
                event.durationNs,
                event.wasForwarded
            )
        }
    }

    /**
     * Records [event] as part of the current launch and forwards it to every attached listener.
     *
     * The event is buffered whether or not anyone is listening: a core that enables RUM in the
     * middle of a launch — after its AppStart, before its TTID — still needs the launch as a whole.
     */
    private fun dispatch(event: Event) {
        pendingEvents.add(event)
        listeners.toList().forEach { forward(it, event) }
    }

    // region RumAppStartupDetector.Listener

    override fun onAppStartupDetected(scenario: RumStartupScenario) {
        // A new launch supersedes the previous one: only the most recent is worth replaying, and
        // this keeps the buffer bounded over the lifetime of the process.
        pendingEvents.clear()
        dispatch(Event.AppStartupDetected(scenario))
    }

    override fun onTTIDComputed(
        scenario: RumStartupScenario,
        durationNs: Long,
        wasForwarded: Boolean
    ) {
        dispatch(Event.TTIDComputed(scenario, durationNs, wasForwarded))
    }

    // endregion
}
