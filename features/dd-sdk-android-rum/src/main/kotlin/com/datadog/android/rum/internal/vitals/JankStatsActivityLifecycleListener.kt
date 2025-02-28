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
import com.datadog.android.rum.internal.domain.FrameMetricsData
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Utility class listening to frame rate information.
 */
internal class JankStatsActivityLifecycleListener(
    private val delegates: List<FrameStateListener>,
    private val internalLogger: InternalLogger,
    private val jankStatsProvider: JankStatsProvider = JankStatsProvider.DEFAULT,
    private var buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : ActivityLifecycleCallbacks, JankStats.OnFrameListener {

    internal val activeWindowsListener = WeakHashMap<Window, JankStats>()

    internal val activeActivities = WeakHashMap<Window, MutableList<WeakReference<Activity>>>()
    internal var display: Display? = null
    private var frameMetricsListener: DDFrameMetricsListener? = null

    private val frameMetricsData = FrameMetricsData()

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

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
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
                // Android N+:
                // android.view.View.removeFrameMetricsListener() may throw it (attempt to remove
                // OnFrameMetricsAvailableListener that was never added). Unclear why, because
                // JankStats registers listener in the constructor, so if we have the instance,
                // listener should be there.
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    { JANK_STATS_TRACKING_DISABLE_ERROR },
                    iae
                )
            } catch (npe: NullPointerException) {
                // Between Android N and Android P (included):
                // android.view.View.removeFrameMetricsListener() may throw an NPE(attempt to remove
                // OnFrameMetricsAvailableListener that was never added). Unclear why, because
                // JankStats registers listener in the constructor, so if we have the instance,
                // listener should be there.
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    { JANK_STATS_TRACKING_DISABLE_ERROR },
                    npe
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
            if (buildSdkVersionProvider.version >= Build.VERSION_CODES.N) {
                unregisterMetricListener(activity.window)
            }
        }
    }

    // endregion

    // region JankStats.OnFrameListener

    override fun onFrame(volatileFrameData: FrameData) {
        for (i in delegates.indices) {
            delegates[i].onFrame(volatileFrameData)
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
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.N && !isKnownWindow) {
            registerMetricListener(window)
        } else if (display == null && buildSdkVersionProvider.version == Build.VERSION_CODES.R) {
            // Fallback - Android 30 allows apps to not run at a fixed 60hz, but didn't yet have
            // Frame Metrics callbacks available
            val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun registerMetricListener(window: Window) {
        if (frameMetricsListener == null) {
            frameMetricsListener = DDFrameMetricsListener()
        }
        // TODO RUM-8799: handler thread can be used instead
        val handler = Handler(Looper.getMainLooper())
        val decorView = window.peekDecorView()

        if (decorView == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Unable to attach JankStatsListener to window, decorView is null" }
            )
            return
        }

        // We need to postpone this operation because isHardwareAccelerated will return
        // false until the view is attached to the window. Note that in this case main looper should be used
        decorView.post {
            // Only hardware accelerated views can be tracked with metrics listener
            if (!decorView.isHardwareAccelerated) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { "Unable to attach JankStatsListener to window, decorView is not hardware accelerated" }
                )
                return@post
            }

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

        @RequiresApi(Build.VERSION_CODES.N)
        override fun onFrameMetricsAvailable(
            window: Window,
            frameMetrics: FrameMetrics,
            dropCountSinceLastInvocation: Int
        ) {
            for (i in delegates.indices) {
                delegates[i].onFrameMetricsData(frameMetricsData.update(frameMetrics))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun FrameMetricsData.update(frameMetrics: FrameMetrics) = apply {
        displayRefreshRate = display?.refreshRate?.toDouble() ?: SIXTY_FPS
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.N) {
            unknownDelayDuration = frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
            inputHandlingDuration = frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
            animationDuration = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
            layoutMeasureDuration = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            drawDuration = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
            syncDuration = frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)
            commandIssueDuration = frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
            swapBuffersDuration = frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
            totalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            firstDrawFrame = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == IS_FIRST_DRAW_FRAME
        }
        @SuppressLint("InlinedApi")
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.O) {
            intendedVsyncTimestamp = frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            vsyncTimestamp = frameMetrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
        }
        @SuppressLint("InlinedApi")
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.S) {
            gpuDuration = frameMetrics.getMetric(FrameMetrics.GPU_DURATION)
            deadline = frameMetrics.getMetric(FrameMetrics.DEADLINE)
        }
    }

    // endregion

    companion object {

        internal const val JANK_STATS_TRACKING_ALREADY_DISABLED_ERROR =
            "Trying to disable JankStats instance which was already disabled before, this" +
                " shouldn't happen."
        internal const val JANK_STATS_TRACKING_DISABLE_ERROR =
            "Failed to disable JankStats tracking"
        private const val SIXTY_FPS: Double = 60.0
        private const val IS_FIRST_DRAW_FRAME = 1L
    }
}
