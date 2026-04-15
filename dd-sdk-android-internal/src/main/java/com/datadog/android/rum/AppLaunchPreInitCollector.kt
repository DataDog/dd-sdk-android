/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.startup.RumFirstDrawTimeReporter
import com.datadog.android.rum.startup.RumFirstDrawTimeReporterImpl
import com.datadog.android.rum.startup.WindowCallbacksRegistryImpl
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton state machine that collects app launch timing data before the RUM SDK is initialized.
 *
 * Designed for cross-platform scenarios (React Native, Flutter) where the native Android RUM SDK
 * may be initialized after the first Activity has already been created. By installing this
 * collector in a [ContentProvider] that runs before SDK initialization, timing data (process start,
 * first Activity onCreate, first frame drawn) is captured unconditionally and then handed off to
 * the RUM SDK when it eventually initializes.
 *
 * **State machine:**
 * ```
 * NOT_INSTALLED --> IDLE --> CAPTURING --> COMPLETE
 *                       \-> CLAIMED   (SDK claimed before first Activity)
 * ```
 *
 * All state transitions use CAS (compare-and-set) for lock-free thread safety.
 *
 * This collector intentionally stores NO RUM SDK types — only primitives, [Boolean] flags, and a
 * [java.lang.ref.WeakReference] to an [android.app.Activity]. It is single-use per process: there
 * is no automatic reset for warm re-launches, as each process has exactly one cold-start lifetime.
 */
object AppLaunchPreInitCollector {

    /**
     * States of the pre-init collector's lifecycle state machine.
     */
    enum class State {
        /** Initial state before [install] is called. */
        NOT_INSTALLED,

        /** [install] has been called; waiting for first Activity onCreate. */
        IDLE,

        /** First Activity onCreate has been intercepted; capturing timing data. */
        CAPTURING,

        /** First frame has been drawn; all timing data is complete and ready for consumption. */
        COMPLETE,

        /**
         * The RUM SDK claimed the collector before the first Activity was created.
         * Timing data will be provided by the SDK's own instrumentation instead.
         */
        CLAIMED
    }

    private val _state = AtomicReference(State.NOT_INSTALLED)

    /**
     * The current state of the collector.
     *
     * Thread-safe: backed by an [AtomicReference]; the returned value reflects the most
     * recently committed state transition.
     */
    val state: State get() = _state.get()

    // region Data fields — public so RumFeature (dd-sdk-android-rum, separate Gradle module) can read them.
    // These are write-once: written during the IDLE->CAPTURING transition, then read after COMPLETE.

    /**
     * The process start time in nanoseconds (monotonic clock, [System.nanoTime] epoch).
     *
     * Written once during the IDLE→CAPTURING transition. Read-only after [State.COMPLETE].
     * `0L` if the collector has not yet entered [State.CAPTURING].
     */
    @Volatile
    var processStartNs: Long = 0L

    /**
     * The timestamp of the first Activity's `onCreate` in nanoseconds ([System.nanoTime]).
     *
     * Written once during the IDLE→CAPTURING transition. Read-only after [State.COMPLETE].
     * `0L` if the collector has not yet entered [State.CAPTURING].
     */
    @Volatile
    var activityOnCreateNs: Long = 0L

    /**
     * The timestamp when the first frame was drawn in nanoseconds ([System.nanoTime]).
     *
     * Written once when the [android.view.ViewTreeObserver.OnDrawListener] fires for the first
     * time. Read-only after [State.COMPLETE]. `0L` until [State.COMPLETE] is reached.
     */
    @Volatile
    var firstFrameNs: Long = 0L

    /**
     * Whether the first Activity was restored from a saved instance state.
     *
     * `true` if `savedInstanceState` was non-null in the Activity lifecycle callback.
     * Written once during the IDLE→CAPTURING transition.
     */
    @Volatile
    var hasSavedInstanceState: Boolean = false

    /**
     * Whether the first Activity was the very first Activity created in this process.
     *
     * `false` if any Activity was destroyed before the collector captured the first frame,
     * indicating a warm re-launch rather than a cold start.
     * Written once during the IDLE→CAPTURING transition.
     */
    @Volatile
    var isFirstActivityForProcess: Boolean = true

    /**
     * Weak reference to the first Activity captured during launch.
     *
     * Written once during the IDLE→CAPTURING transition. The referent may have been
     * garbage-collected by the time the RUM SDK reads it; callers must null-check [WeakReference.get].
     */
    @Volatile
    var activity: WeakReference<Activity>? = null

    // endregion

    private val firstFrameCallbacks = CopyOnWriteArrayList<(Long) -> Unit>()
    private var _application: Application? = null

    /** Private flag tracking whether any Activity has been destroyed (process is warm). */
    private var _isFirstActivityForProcess: Boolean = true

    /** SDK version provider — injectable for testing. */
    internal var buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT

    /** Handler factory — injectable for testing. */
    internal var handlerFactory: () -> Handler = { Handler(Looper.getMainLooper()) }

    /** First-draw time reporter factory — injectable for testing. */
    internal var firstDrawTimeReporterFactory: (Handler) -> RumFirstDrawTimeReporter = { handler ->
        RumFirstDrawTimeReporterImpl(
            timeProviderNs = { System.nanoTime() },
            windowCallbacksRegistry = WindowCallbacksRegistryImpl(),
            handler = handler
        )
    }

    // region Lifecycle callback

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {

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

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            // Once an Activity is destroyed, subsequent activities are not "first for process"
            _isFirstActivityForProcess = false
        }
    }

    // endregion

    // region Private helpers

    private fun onBeforeActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // CAS: only the first caller transitions IDLE -> CAPTURING; concurrent claim() loses
        if (!_state.compareAndSet(State.IDLE, State.CAPTURING)) {
            Log.d(TAG, "onBeforeActivityCreated: CAS failed — state is ${_state.get()}, not IDLE; skipping")
            return
        }

        activityOnCreateNs = System.nanoTime()
        hasSavedInstanceState = savedInstanceState != null
        this.activity = WeakReference(activity)
        isFirstActivityForProcess = _isFirstActivityForProcess
        processStartNs = computeProcessStartNs()

        Log.d(
            TAG,
            "IDLE→CAPTURING: activity=${activity.javaClass.simpleName}" +
                " hasSavedInstanceState=$hasSavedInstanceState" +
                " isFirstActivityForProcess=$isFirstActivityForProcess" +
                " processStartNs=$processStartNs" +
                " activityOnCreateNs=$activityOnCreateNs" +
                " gapMs=${(activityOnCreateNs - processStartNs) / 1_000_000}"
        )

        // Unregister lifecycle callbacks — we've captured what we need from the first Activity
        _application?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)

        // Subscribe to first frame drawn — transitions CAPTURING -> COMPLETE
        val handler = handlerFactory()
        val reporter = firstDrawTimeReporterFactory(handler)
        reporter.subscribeToFirstFrameDrawn(activity, object : RumFirstDrawTimeReporter.Callback {
            override fun onFirstFrameDrawn(timestampNs: Long) {
                firstFrameNs = timestampNs
                _state.compareAndSet(State.CAPTURING, State.COMPLETE)

                Log.d(
                    TAG,
                    "CAPTURING->COMPLETE: first frame drawn" +
                        " firstFrameNs=$firstFrameNs" +
                        " ttidMs=${(firstFrameNs - activityOnCreateNs) / 1_000_000}" +
                        " totalMs=${(firstFrameNs - processStartNs) / 1_000_000}" +
                        " pendingCallbacks=${firstFrameCallbacks.size}"
                )

                // Drain all enqueued callbacks
                val callbacks = firstFrameCallbacks.toList()
                firstFrameCallbacks.clear()
                callbacks.forEach { cb -> cb(firstFrameNs) }
            }
        })
    }

    /**
     * Compute the process start time in nanoseconds.
     *
     * On API 24+, uses Process.getStartElapsedRealtime() to back-compute from the current
     * elapsed realtime clock. Applies a two-direction OEM sanity check:
     * - If computed time is after DdRumContentProvider.createTimeNs (impossible), fall back.
     * - If computed time is more than 10s before createTimeNs (unreasonable), fall back.
     *
     * On API 23, falls back directly to DdRumContentProvider.createTimeNs.
     */
    @Suppress("NewApi") // Process.getStartElapsedRealtime is guarded by isAtLeastN check
    internal fun computeProcessStartNs(): Long {
        if (!buildSdkVersionProvider.isAtLeastN) {
            return DdRumContentProvider.createTimeNs
        }
        val nowNs = System.nanoTime()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val diffMs = nowElapsedMs - Process.getStartElapsedRealtime()
        val computed = nowNs - TimeUnit.MILLISECONDS.toNanos(diffMs)
        val fallback = DdRumContentProvider.createTimeNs
        val isAfterFallback = computed > fallback
        val isTooFarBefore = fallback - computed > PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS
        return if (isAfterFallback || isTooFarBefore) fallback else computed
    }

    // endregion

    // region Public API

    /**
     * Install the collector into the given [Application].
     *
     * Transitions [State.NOT_INSTALLED] → [State.IDLE] and registers
     * [Application.ActivityLifecycleCallbacks] to capture the first Activity's creation.
     *
     * **Idempotent** — if the collector is already in any state other than [State.NOT_INSTALLED],
     * this call is a no-op.
     *
     * Must be called as early as possible in the process lifetime, typically from a
     * [android.content.ContentProvider.onCreate] that is registered before the RUM SDK initializes.
     *
     * @param application The [Application] instance to register lifecycle callbacks on.
     */
    fun install(application: Application) {
        if (!_state.compareAndSet(State.NOT_INSTALLED, State.IDLE)) {
            Log.d(TAG, "install() called but already in state ${_state.get()}, skipping")
            return
        }
        _application = application
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        Log.d(TAG, "Installed — state: IDLE, ActivityLifecycleCallbacks registered")
    }

    /**
     * Claim the pre-launch data for use by the RUM SDK.
     *
     * Transitions [State.IDLE] → [State.CLAIMED] using a CAS operation, and unregisters
     * lifecycle callbacks. Called by `RumFeature` when the SDK initializes before the first
     * Activity has been created, so the SDK will perform its own TTID measurement instead.
     *
     * **Race semantics:** Only the first caller that finds the state as [State.IDLE] will succeed.
     * Concurrent calls from the Activity lifecycle callbacks lose the CAS and are ignored.
     *
     * @return `true` if the claim succeeded (state was [State.IDLE]); `false` if the collector was
     *   in any other state (e.g., already [State.CAPTURING] or [State.CLAIMED]).
     */
    fun claim(): Boolean {
        val success = _state.compareAndSet(State.IDLE, State.CLAIMED)
        if (success) {
            _application?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            Log.d(TAG, "Claimed — SDK initialized before first Activity (IDLE→CLAIMED); callbacks unregistered")
        } else {
            Log.d(TAG, "claim() CAS failed — state is ${_state.get()}, not IDLE; collector already in use")
        }
        return success
    }

    /**
     * Register a callback to be invoked with the first-frame timestamp.
     *
     * **TOCTOU-safe:** if the collector is already in [State.COMPLETE], [cb] is invoked
     * synchronously on the calling thread before this method returns. Otherwise, [cb] is enqueued
     * in a [java.util.concurrent.CopyOnWriteArrayList] and will be drained (on the main thread)
     * the moment [State.COMPLETE] is reached.
     *
     * A double-check pattern closes the TOCTOU window: after enqueuing, the state is re-read.
     * If it has since transitioned to [State.COMPLETE], the callback is removed from the list and
     * invoked immediately, guaranteeing exactly-once delivery.
     *
     * @param cb Callback that receives the first-frame timestamp in nanoseconds ([System.nanoTime]).
     */
    fun addFirstFrameCallback(cb: (Long) -> Unit) {
        if (_state.get() == State.COMPLETE) {
            Log.d(TAG, "addFirstFrameCallback: already COMPLETE, invoking callback synchronously")
            cb(firstFrameNs)
            return
        }
        firstFrameCallbacks.add(cb)
        // Double-check: state may have transitioned to COMPLETE between the first check and the add
        if (_state.get() == State.COMPLETE) {
            if (firstFrameCallbacks.remove(cb)) {
                Log.d(TAG, "addFirstFrameCallback: TOCTOU race — COMPLETE during enqueue, invoking synchronously")
                cb(firstFrameNs)
            }
        } else {
            Log.d(TAG, "addFirstFrameCallback: state=${_state.get()}, callback enqueued (${firstFrameCallbacks.size} total)")
        }
    }

    // endregion

    // region Constants

    internal const val TAG = "DD/AppLaunch"

    /**
     * Threshold for the two-direction OEM clock sanity check.
     * If computed process start time is more than 10s before DdRumContentProvider.createTimeNs,
     * it is considered an OEM bug and createTimeNs is used as fallback.
     */
    internal val PROCESS_START_TO_CP_START_DIFF_THRESHOLD_NS = 10.seconds.inWholeNanoseconds

    // endregion

    // region Testing support

    /**
     * Reset the collector to its initial state.
     *
     * Restores all fields to their default values, equivalent to a fresh process start.
     * **For use in unit tests only** — production code must never call this method.
     */
    @VisibleForTesting
    internal fun reset() {
        _state.set(State.NOT_INSTALLED)
        processStartNs = 0L
        activityOnCreateNs = 0L
        firstFrameNs = 0L
        hasSavedInstanceState = false
        isFirstActivityForProcess = true
        _isFirstActivityForProcess = true
        activity = null
        firstFrameCallbacks.clear()
        _application = null
        buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
        handlerFactory = { Handler(Looper.getMainLooper()) }
        firstDrawTimeReporterFactory = { handler ->
            RumFirstDrawTimeReporterImpl(
                timeProviderNs = { System.nanoTime() },
                windowCallbacksRegistry = WindowCallbacksRegistryImpl(),
                handler = handler
            )
        }
    }

    // endregion
}
