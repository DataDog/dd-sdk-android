/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.system.DefaultBuildSdkVersionProvider
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

/**
 * Utility class listening to frame rate information.
 */
internal class JankStatsActivityLifecycleListener(
    private val vitalObserver: VitalObserver,
    private val internalLogger: InternalLogger,
    private val jankStatsProvider: JankStatsProvider = JankStatsProvider.DEFAULT,
    private var screenRefreshRate: Double = 60.0,
    private var buildSdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider()

) : ActivityLifecycleCallbacks, JankStats.OnFrameListener {

    internal val activeWindowsListener = WeakHashMap<Window, JankStats>()

    internal val activeActivities = WeakHashMap<Window, MutableList<WeakReference<Activity>>>()
    internal var display: Display? = null
    private var frameMetricsListener: DDFrameMetricsListener? = null
    internal var frameDeadline = SIXTEEN_MS_NS

    // region ActivityLifecycleCallbacks
    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        val window = activity.window
        trackActivity(window, activity)

        // Keep track of isKnownWindow before calling trackWindowJankStats otherwise it'll always be true
        val isKnownWindow = activeWindowsListener.containsKey(window)
        trackWindowJankStats(window)

        trackWindowMetrics(isKnownWindow, window, activity)
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
    }

    @MainThread
    override fun onActivityPaused(activity: Activity) {
    }

    @Suppress("NestedBlockDepth")
    @MainThread
    override fun onActivityStopped(activity: Activity) {
        val window = activity.window
        if (!activeActivities.containsKey(window)) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Activity stopped but window was not tracked" }
            )
        }
        val list = activeActivities[window] ?: mutableListOf()
        list.removeAll {
            it.get() == null || it.get() == activity
        }
        activeActivities[window] = list
        if (list.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Disabling jankStats for window $window" }
            )
            try {
                activeWindowsListener[window]?.let {
                    if (it.isTrackingEnabled) {
                        it.isTrackingEnabled = false
                    } else {
                        internalLogger.log(
                            InternalLogger.Level.ERROR,
                            InternalLogger.Target.TELEMETRY,
                            { JANK_STATS_TRACKING_ALREADY_DISABLED_ERROR }
                        )
                    }
                }
            } catch (iae: IllegalArgumentException) {
                // android.view.View.removeFrameMetricsListener may throw it (attempt to remove
                // OnFrameMetricsAvailableListener that was never added). Unclear why, because
                // JankStats registers listener in the constructor, so if we have the instance,
                // listener should be there.
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    { JANK_STATS_TRACKING_DISABLE_ERROR },
                    iae
                )
            }
        }
    }

    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    @SuppressLint("NewApi")
    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        if (activeActivities[activity.window].isNullOrEmpty()) {
            activeWindowsListener.remove(activity.window)
            activeActivities.remove(activity.window)
            if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.S) {
                unregisterMetricListener(activity.window)
            }
        }
    }

    // endregion

    // region JankStats.OnFrameListener

    override fun onFrame(volatileFrameData: FrameData) {
        val durationNs = volatileFrameData.frameDurationUiNanos
        if (durationNs > 0.0) {
            var frameRate = (ONE_SECOND_NS / durationNs)

            if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.S) {
                screenRefreshRate = ONE_SECOND_NS / frameDeadline
            } else if (buildSdkVersionProvider.version() == Build.VERSION_CODES.R) {
                screenRefreshRate = display?.refreshRate?.toDouble() ?: SIXTY_FPS
            }

            // If normalized frame rate is still at over 60fps it means the frame rendered
            // quickly enough for the devices refresh rate.
            frameRate = (frameRate * (SIXTY_FPS / screenRefreshRate)).coerceAtMost(MAX_FPS)

            if (frameRate > MIN_FPS) {
                vitalObserver.onNewSample(frameRate)
            }
        }
    }

    // endregion

    // region Internal

    private fun trackActivity(window: Window, activity: Activity) {
        val list = activeActivities[window] ?: mutableListOf()
        list.add(WeakReference(activity))
        activeActivities[window] = list
    }

    @MainThread
    private fun trackWindowJankStats(window: Window) {
        val knownJankStats = activeWindowsListener[window]
        if (knownJankStats != null) {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Resuming jankStats for window $window" }
            )
            knownJankStats.isTrackingEnabled = true
        } else {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Starting jankStats for window $window" }
            )
            val jankStats = jankStatsProvider.createJankStatsAndTrack(window, this, internalLogger)
            if (jankStats == null) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { "Unable to create JankStats" }
                )
            } else {
                activeWindowsListener[window] = jankStats
            }
        }
    }

    @SuppressLint("NewApi")
    @MainThread
    private fun trackWindowMetrics(isKnownWindow: Boolean, window: Window, activity: Activity) {
        if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.S && !isKnownWindow) {
            registerMetricListener(window)
        } else if (display == null && buildSdkVersionProvider.version() == Build.VERSION_CODES.R) {
            // Fallback - Android 30 allows apps to not run at a fixed 60hz, but didn't yet have
            // Frame Metrics callbacks available
            val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerMetricListener(window: Window) {
        if (frameMetricsListener == null) {
            frameMetricsListener = DDFrameMetricsListener()
        }
        val handler = Handler(Looper.getMainLooper())
        // Only hardware accelerated views can be tracked with metrics listener
        if (window.peekDecorView()?.isHardwareAccelerated == true) {
            frameMetricsListener?.let { listener ->
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Listener can't be null here
                    window.addOnFrameMetricsAvailableListener(listener, handler)
                } catch (e: IllegalStateException) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { "Unable to attach JankStatsListener to window" },
                        e
                    )
                }
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Unable to attach JankStatsListener to window, decorView is null or not hardware accelerated" }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun unregisterMetricListener(window: Window) {
        try {
            window.removeOnFrameMetricsAvailableListener(frameMetricsListener)
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to detach JankStatsListener to window, most probably because it wasn't attached" },
                e
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    inner class DDFrameMetricsListener : Window.OnFrameMetricsAvailableListener {

        @RequiresApi(Build.VERSION_CODES.S)
        override fun onFrameMetricsAvailable(
            window: Window,
            frameMetrics: FrameMetrics,
            dropCountSinceLastInvocation: Int
        ) {
            frameDeadline = frameMetrics.getMetric(FrameMetrics.DEADLINE)
        }
    }

    // endregion

    companion object {

        internal const val JANK_STATS_TRACKING_ALREADY_DISABLED_ERROR =
            "Trying to disable JankStats instance which was already disabled before, this" +
                    " shouldn't happen."
        internal const val JANK_STATS_TRACKING_DISABLE_ERROR =
            "Failed to disable JankStats tracking"

        private val ONE_SECOND_NS: Double = TimeUnit.SECONDS.toNanos(1).toDouble()

        private const val MIN_FPS: Double = 1.0
        private const val MAX_FPS: Double = 60.0
        private const val SIXTY_FPS: Double = 60.0
        private const val SIXTEEN_MS_NS: Long = 16666666
    }
}
