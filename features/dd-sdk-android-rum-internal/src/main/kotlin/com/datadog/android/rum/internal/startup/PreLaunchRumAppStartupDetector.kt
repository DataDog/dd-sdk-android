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
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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
 * accumulate, each with the Activity predicate of the core that registered it.
 *
 * **Threading**: [install] and all [RumAppStartupDetector.Listener] callbacks run on the main
 * thread, and [attach]/[detach] are dispatched there by [Rum.enable], so the listener and event
 * state needs no synchronization. [isInstalled] is the exception: [Rum.enable] reads it from
 * whichever thread it was called on — a background thread for React Native and Flutter — before
 * any main-thread hop, so the field behind it is volatile.
 */
@Suppress("UnsafeThirdPartyFunctionCall")
object PreLaunchRumAppStartupDetector : RumAppStartupDetector.Listener {

    private sealed class Event {
        abstract val scenario: RumStartupScenario

        data class AppStartupDetected(override val scenario: RumStartupScenario) : Event()
        data class TTIDComputed(
            override val scenario: RumStartupScenario,
            val durationNs: Long,
            val wasForwarded: Boolean,
            val forwardedActivity: WeakReference<Activity>?
        ) : Event()
    }

    /**
     * A listener paired with the Activity predicate of the SDK core that registered it.
     *
     * The predicate cannot live on the singleton: several [com.datadog.android.api.SdkCore]
     * instances may enable RUM in the same process, each with its own
     * `appStartupActivityPredicate`, and one core's narrower predicate must not decide what the
     * others see.
     */
    private class Registration(
        val listener: RumAppStartupDetector.Listener,
        val activityPredicate: (Activity) -> Boolean
    ) {
        /**
         * The scenario this listener has been told about and is still awaiting a TTID for.
         *
         * A TTID is only forwarded to a listener that received the matching AppStart: consumers
         * index the TTID against the launch it belongs to, and one without its AppStart lands on a
         * negative index with no scenario to associate a TTFD with.
         *
         * It doubles as the record that [activityPredicate] accepted the scenario Activity, which
         * is why [forwardIfAccepted] does not test that Activity again when the TTID arrives.
         */
        var startedScenario: RumStartupScenario? = null
    }

    // Volatile: written on the main thread from install(), but read through isInstalled on the
    // thread that called Rum.enable(). Nothing orders those two, so without this a caller could
    // observe null after installation and build a second detector, losing the launch this module
    // exists to capture.
    @Volatile
    private var detectorImpl: RumAppStartupDetector? = null
    private val registrations = mutableListOf<Registration>()

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
    val attachedListenerCount: Int get() = registrations.size

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
            // Union of the attached cores' predicates — an Activity is worth measuring as long as
            // at least one of them wants it, and the per-listener filtering in
            // forwardIfAccepted() decides who actually hears about it. Before any core attaches
            // nothing is known about the user's configuration, so everything is accepted.
            //
            // Known limitation: attach() filters the events a core receives, but leaves the
            // pending scenario and tracked Activities the detector accumulated under this
            // permissive predicate untouched. An Activity a core excludes can therefore hold the
            // single pending scenario slot, so a qualifying Activity created while it is still
            // alive is folded into that scenario rather than opening its own, and delivery
            // filtering drops the whole launch. Left as-is: it needs a custom
            // AppStartupActivityPredicate and a multi-Activity launch, neither of which the
            // cross-platform SDKs this module exists for produce.
            appStartupActivityPredicate = { activity ->
                registrations.isEmpty() || registrations.any { it.activityPredicate(activity) }
            },
            rumFirstDrawTimeReporter = RumFirstDrawTimeReporterImpl(
                timeProviderNs = { System.nanoTime() },
                windowCallbacksRegistry = WindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper())
            )
        )
    }

    /**
     * Attaches [listener] and replays the most recent launch to it.
     *
     * Several SDK cores may enable RUM in the same process, so listeners accumulate rather than
     * replace each other, and each keeps its own [activityPredicate]. [pendingEvents] is
     * *replayed*, not consumed, so a listener attaching after the SDK-less capture — or in the
     * middle of a launch another core is already hearing about live — still sees the whole launch.
     *
     * Events were captured with a permissive predicate, because no core's configuration is known
     * before the SDK initializes. Each one is therefore re-validated against [activityPredicate]
     * before it is forwarded, and a TTID whose AppStart this listener did not receive is dropped,
     * so a launch is never delivered half-way.
     *
     * Must be called on the main thread.
     *
     * @param listener The listener to receive startup events.
     * @param activityPredicate Decides whether an Activity qualifies as a startup Activity for the
     * core owning [listener].
     */
    fun attach(
        listener: RumAppStartupDetector.Listener,
        activityPredicate: (Activity) -> Boolean
    ) {
        val registration = Registration(listener, activityPredicate)
        registrations.add(registration)
        pendingEvents.toList().forEach { forwardIfAccepted(registration, it) }
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
     * it still holds the matching pending scenario and will emit the TTID, and dropping the AppStart
     * now would leave that TTID with nothing to be indexed against — [forwardIfAccepted] would
     * discard it, and the launch would be lost to whichever core enables RUM next.
     *
     * Must be called on the main thread.
     *
     * @param listener The listener to remove.
     */
    fun detach(listener: RumAppStartupDetector.Listener) {
        registrations.removeAll { it.listener === listener }
        // The buffer holds one launch at a time, so the presence of a TTID means this one is
        // complete and every attached listener has already had the whole of it.
        val isLaunchInFlight = pendingEvents.isNotEmpty() &&
            pendingEvents.none { it is Event.TTIDComputed }
        if (registrations.isEmpty() && !isLaunchInFlight) {
            pendingEvents.clear()
        }
    }

    /**
     * Applies [Registration.activityPredicate] to an Activity an event was measured against.
     *
     * An Activity that has already been garbage collected cannot be validated; those are accepted
     * rather than dropped, since by the time a cross-platform SDK calls `Rum.enable()` the launch
     * Activity may well be gone, and silently discarding every such launch would defeat the purpose
     * of the pre-launch module. A missing reference — no Activity forwarded the draw — is likewise
     * nothing to reject.
     */
    private fun isActivityAccepted(
        activity: WeakReference<Activity>?,
        registration: Registration
    ): Boolean {
        val resolved = activity?.get()
        return resolved == null || registration.activityPredicate(resolved)
    }

    /**
     * Forwards [event] to [registration], unless its predicate rejects it or it is a TTID for a
     * launch this listener never heard the AppStart of.
     *
     * Each Activity is tested exactly once, by the event that introduces it: the scenario Activity
     * when the AppStart is forwarded, the forwarding Activity when the TTID is. Re-testing the
     * scenario Activity at TTID time would contradict
     * [com.datadog.android.rum.startup.AppStartupActivityPredicate], which is documented as being
     * evaluated during Activity creation, and a predicate reading mutable Activity state — say
     * `!activity.isFinishing` — could then accept the AppStart and reject the TTID, leaving the
     * consumer with half a launch.
     */
    private fun forwardIfAccepted(registration: Registration, event: Event) {
        when (event) {
            is Event.AppStartupDetected -> {
                if (isActivityAccepted(event.scenario.activity, registration)) {
                    registration.startedScenario = event.scenario
                    registration.listener.onAppStartupDetected(event.scenario)
                }
            }
            is Event.TTIDComputed -> {
                // A startedScenario match is the decision already taken for the scenario Activity
                // when its AppStart was forwarded, so the predicate is not re-run against it.
                val startedThisScenario = registration.startedScenario === event.scenario
                if (startedThisScenario &&
                    isActivityAccepted(event.forwardedActivity, registration)
                ) {
                    registration.startedScenario = null
                    registration.listener.onTTIDComputed(
                        event.scenario,
                        event.durationNs,
                        event.wasForwarded,
                        event.forwardedActivity
                    )
                }
            }
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
        registrations.toList().forEach { forwardIfAccepted(it, event) }
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
        wasForwarded: Boolean,
        forwardedActivity: WeakReference<Activity>?
    ) {
        dispatch(Event.TTIDComputed(scenario, durationNs, wasForwarded, forwardedActivity))
    }

    // endregion

    // region Internal

    /**
     * Back-projects the process start onto the `System.nanoTime()` timebase.
     *
     * Everything downstream of this object measures with `System.nanoTime()`, so the elapsed
     * delta has to come from the same clock: `SystemClock.uptimeMillis()` and
     * [Process.getStartUptimeMillis] are both CLOCK_MONOTONIC and both exclude deep sleep.
     * Pairing them with `elapsedRealtime()` instead would add any deep sleep since process start
     * to the delta, over-projecting the start time and inflating every TTID measured from it.
     */
    // NewApi: Process.getStartUptimeMillis is guarded by the isAtLeastN check below.
    // PreferTimeProvider: see install() — no TimeProvider exists this early in the process.
    @Suppress("NewApi", "PreferTimeProvider")
    internal fun computeProcessStartNs(): Long {
        if (!BuildSdkVersionProvider.DEFAULT.isAtLeastN) {
            return DdRumContentProvider.createTimeNs
        }
        val nowNs = System.nanoTime()
        val nowUptimeMs = SystemClock.uptimeMillis()
        val diffMs = nowUptimeMs - Process.getStartUptimeMillis()
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
