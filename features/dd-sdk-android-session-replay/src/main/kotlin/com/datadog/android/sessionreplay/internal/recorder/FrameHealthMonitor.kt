/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.datadog.android.internal.system.BuildSdkVersionProvider
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Tracks whether recently rendered frames have been missing their own device's real frame
 * deadline, using [Window.addOnFrameMetricsAvailableListener] rather than a fixed per-second cost
 * budget. "Is this device currently missing its own deadline" is relative to whatever that
 * device's actual refresh interval is (16.6ms at 60Hz, 8.3ms at 120Hz, ...), unlike a fixed
 * ms-per-second constant, which means something different depending on the device's refresh rate
 * and doesn't distinguish an occasional burst of genuinely expensive work from a sustained,
 * cheap-but-frequent trigger that happens to add up over a whole second.
 *
 * Requires API 24+ ([Window.addOnFrameMetricsAvailableListener]); on older devices every method
 * here is a no-op and [isDegraded] always returns false, so behavior for those devices is
 * unchanged.
 *
 * Registration is scoped to the lifetime of window interception (see [SessionReplayRecorder]),
 * not lazily activated on a suspected burst - registering costs one lightweight callback per
 * frame on a dedicated background thread, not main-thread work, so the always-on-while-recording
 * cost is small enough that a separate burst-detection stage wasn't judged worth it. The
 * background thread itself is still created lazily, on the first real [startTracking] call, so a
 * caller that never calls it - e.g. [SessionReplayRecorder] when dynamic optimization is disabled
 * entirely - never pays for an idle thread it will never use.
 */
@Suppress("TooManyFunctions")
internal class FrameHealthMonitor(
    private val rollingWindowSize: Int = DEFAULT_ROLLING_WINDOW_SIZE,
    private val degradedThreshold: Int = DEFAULT_DEGRADED_THRESHOLD,
    private val implausibleReadingCircuitBreaker: Int = DEFAULT_IMPLAUSIBLE_CIRCUIT_BREAKER,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) {

    private val isSupported: Boolean = buildSdkVersionProvider.isAtLeastN

    // Only ever touched from @MainThread methods below.
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    // Per-window, not shared: two windows open at once (e.g. an Activity behind a Dialog) have
    // independent frame timings, and blending them into one rolling buffer would let one window's
    // jank contaminate - or dilute - a reading that's supposed to describe a different window.
    // Only ever written from @MainThread methods (startTracking) - each window's own background
    // callback (see onFrameMetrics) only ever mutates the one WindowFrameState it captured when
    // that window's listener was registered, never this map itself.
    private val frameStatesByWindow = WeakHashMap<Window, WindowFrameState>()

    // Weak keys: a window removed without an explicit stopTracking call must not be held forever.
    private val listenersByWindow = WeakHashMap<Window, Window.OnFrameMetricsAvailableListener>()

    /**
     * Starts tracking frame health for [window]. Safe to call more than once for the same window.
     */
    @MainThread
    @Suppress("UnsafeThirdPartyFunctionCall", "SwallowedException") // window attachment is a real race
    fun startTracking(window: Window) {
        if (!isSupported || listenersByWindow.containsKey(window)) return
        val handler = ensureHandler() ?: return
        val refreshPeriodNs = refreshPeriodNs(window)
        val state = WindowFrameState(rollingWindowSize)
        val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            onFrameMetrics(frameMetrics, refreshPeriodNs, state)
        }
        try {
            window.addOnFrameMetricsAvailableListener(listener, handler)
            listenersByWindow[window] = listener
            frameStatesByWindow[window] = state
        } catch (e: IllegalStateException) {
            // Window not attached yet, or no hardware renderer - nothing to monitor here.
        }
    }

    @MainThread
    @Suppress("UnsafeThirdPartyFunctionCall", "ReturnCount") // fresh thread, can't already be started
    private fun ensureHandler(): Handler? {
        if (!isSupported) return null
        handler?.let { return it }
        val newThread = HandlerThread("dd-sr-frame-health").apply { start() }
        handlerThread = newThread
        return Handler(newThread.looper).also { handler = it }
    }

    /**
     * Stops tracking frame health for [window]. Safe to call for a window that was never tracked.
     */
    @MainThread
    @Suppress("UnsafeThirdPartyFunctionCall", "SwallowedException") // window state race, not our data
    fun stopTracking(window: Window) {
        if (!isSupported) return
        val listener = listenersByWindow.remove(window) ?: return
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (e: IllegalStateException) {
            // Already detached.
        }
    }

    /** Stops tracking every currently tracked window. */
    @MainThread
    fun stopTrackingAll() {
        listenersByWindow.keys.toList().forEach { stopTracking(it) }
    }

    /**
     * Releases this monitor's background thread, if one was ever actually created. Meant to be
     * called once, when the owning recorder itself is torn down (see
     * [SessionReplayRecorder.unregisterCallbacks]) - not on every [stopTrackingAll], since
     * recording can be paused and resumed many times within the same recorder's lifetime. A
     * [startTracking] call after [shutdown] lazily recreates the thread rather than registering
     * against a dead one.
     */
    @MainThread
    fun shutdown() {
        stopTrackingAll()
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    /**
     * Whether recent frames have been missing their own deadline often enough to consider this
     * device currently degraded, on at least one currently-tracked window. Always false until a
     * window has actually observed enough frames, and a window stays excluded permanently once
     * too many implausible readings disabled it - see [implausibleReadingCircuitBreaker].
     */
    fun isDegraded(): Boolean {
        if (!isSupported) return false
        return frameStatesByWindow.values.any { isDegraded(it) }
    }

    private fun isDegraded(state: WindowFrameState): Boolean {
        if (state.disabledDueToImplausibleData || state.framesObserved < rollingWindowSize) {
            return false
        }
        var jankyCount = 0
        for (i in 0 until rollingWindowSize) {
            @Suppress("UnsafeThirdPartyFunctionCall") // i is always within the array's own bounds
            jankyCount += state.recentFrames.get(i)
        }
        return jankyCount >= degradedThreshold
    }

    private fun refreshPeriodNs(window: Window): Long {
        @Suppress("DEPRECATION")
        val refreshRate = window.windowManager?.defaultDisplay?.refreshRate
            ?.takeIf { it > 0f }
            ?: DEFAULT_REFRESH_RATE_HZ
        return (NANOS_PER_SECOND / refreshRate).toLong()
    }

    // Invoked on the dedicated background handler thread - never the main thread. Operates
    // directly on the WindowFrameState captured when this window's listener was registered, so
    // this never touches frameStatesByWindow itself - that map is main-thread-only, same as
    // listenersByWindow.
    @RequiresApi(Build.VERSION_CODES.N)
    private fun onFrameMetrics(metrics: FrameMetrics, refreshPeriodNs: Long, state: WindowFrameState) {
        recordFrameDuration(metrics.getMetric(FrameMetrics.TOTAL_DURATION), refreshPeriodNs, state)
    }

    /**
     * Testable entry point for [window]'s classification logic: takes a plain duration rather
     * than needing a real (or mocked) [FrameMetrics] instance, and resolves (creating on first
     * use) the [WindowFrameState] the real [onFrameMetrics] path would already have captured via
     * [startTracking].
     */
    @VisibleForTesting
    internal fun recordFrameDuration(durationNs: Long, refreshPeriodNs: Long, window: Window) {
        val state = frameStatesByWindow.getOrPut(window) { WindowFrameState(rollingWindowSize) }
        recordFrameDuration(durationNs, refreshPeriodNs, state)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // floorMod/set here can't throw given these inputs
    private fun recordFrameDuration(durationNs: Long, refreshPeriodNs: Long, state: WindowFrameState) {
        if (durationNs <= 0 || durationNs > IMPLAUSIBLE_DURATION_NS) {
            // Some OEM builds are known to report unreliable FrameMetrics values. Fail toward
            // this window simply staying inert (isDegraded() never true for it) rather than
            // acting on data that can't be trusted.
            if (state.consecutiveImplausibleReadings.incrementAndGet() >= implausibleReadingCircuitBreaker) {
                state.disabledDueToImplausibleData = true
            }
            return
        }
        state.consecutiveImplausibleReadings.set(0)
        val isJanky = durationNs > refreshPeriodNs
        val index = Math.floorMod(state.nextWriteIndex.getAndIncrement(), rollingWindowSize)
        state.recentFrames.set(index, if (isJanky) 1 else 0)
        if (state.framesObserved < rollingWindowSize) {
            state.framesObserved++
        }
    }

    /**
     * One window's own rolling jank history. Every field here is mutated only from the single
     * background handler thread that window's [Window.OnFrameMetricsAvailableListener] runs on,
     * and read only from the main thread (via [isDegraded]) - the same cross-thread contract
     * [FrameHealthMonitor] itself had before this state was split out per window.
     */
    private class WindowFrameState(rollingWindowSize: Int) {
        @Suppress("UnsafeThirdPartyFunctionCall") // rollingWindowSize is always non-negative
        val recentFrames = AtomicIntegerArray(rollingWindowSize)
        val nextWriteIndex = AtomicInteger(0)
        val consecutiveImplausibleReadings = AtomicInteger(0)

        @Volatile
        var disabledDueToImplausibleData = false

        @Volatile
        var framesObserved = 0
    }

    private companion object {
        const val DEFAULT_ROLLING_WINDOW_SIZE = 8
        const val DEFAULT_DEGRADED_THRESHOLD = 3
        const val DEFAULT_IMPLAUSIBLE_CIRCUIT_BREAKER = 20
        const val DEFAULT_REFRESH_RATE_HZ = 60f
        const val NANOS_PER_SECOND = 1_000_000_000L

        // No real frame takes this long - a reading beyond this is instrument noise, not signal.
        const val IMPLAUSIBLE_DURATION_NS = 1_000_000_000L
    }
}
