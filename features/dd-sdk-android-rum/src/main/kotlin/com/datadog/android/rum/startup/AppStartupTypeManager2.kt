/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.metrics.performance.FrameData
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.utils.subscribeToFirstDrawFinished
import com.datadog.android.internal.utils.subscribeToFirstDrawFinishedFrameCallback
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.domain.FrameMetricsData
import com.datadog.android.rum.internal.vitals.FrameStateListener
import com.datadog.android.rum.startup.newapi.AppInfoRepo
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private val START_GAP_THRESHOLD = 5.seconds

private enum class AppStartType {
    COLD,
    WARM,
    HOT
}

internal class AppStartupTypeManager2(
    private val context: Context,
    private val sdkCore: InternalSdkCore,
): Application.ActivityLifecycleCallbacks, FrameStateListener {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess = true

    private val handler = Handler(Looper.getMainLooper())

    private var isInBackground = true

    private val resumedActivities = mutableSetOf<WeakReference<Activity>>()

    private val waitingForStart = AtomicReference<String>(null)

    private val appInfoRepo = AppInfoRepo.create(context)

    private val processObserver = object: DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            Log.w("AppStartupTypeManager2", "process onCreate")
        }

        override fun onDestroy(owner: LifecycleOwner) {
            Log.w("AppStartupTypeManager2", "process onDestroy")
        }
        override fun onPause(owner: LifecycleOwner) {
            Log.w("AppStartupTypeManager2", "process onPause")
        }

        override fun onResume(owner: LifecycleOwner) {
            Log.w("AppStartupTypeManager2", "process onResume")
        }

        override fun onStart(owner: LifecycleOwner) {
            isInBackground = false
            Log.w("AppStartupTypeManager2", "process onStart")
        }

        override fun onStop(owner: LifecycleOwner) {
            isInBackground = true
            Log.w("AppStartupTypeManager2", "process onStop")
        }
    }

    init {
        (context as Application).registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    private var initialPointNanos: Long = 0
    private var appStartType: AppStartType? = null

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        initialPointNanos = System.nanoTime()
    }

    override fun onActivityPreStarted(activity: Activity) {
        initialPointNanos = System.nanoTime()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("DatadogInternalApiUsage")
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.w("AppStartupTypeManager2", "onActivityCreated $activity")
        numberOfActivities++

        if (numberOfActivities == 1 && !isChangingConfigurations) {
            val processStartedInForeground = DdRumContentProvider.processImportance == IMPORTANCE_FOREGROUND
            val now = System.nanoTime()
            val gap = (now - sdkCore.appStartTimeNs).nanoseconds

            if (isFirstActivityForProcess) {
                if (!processStartedInForeground || gap > START_GAP_THRESHOLD) {
                    appStartType = AppStartType.WARM
                    startTrackingStart(activity, "warm_3")

                    Log.w("AppStartupTypeManager2", "scenario 3")
                } else {
                    appStartType = AppStartType.COLD
                    startTrackingStart(activity, "cold")

                    Log.w("AppStartupTypeManager2", "scenario 1")
                }
            } else {
                appStartType = AppStartType.WARM
                startTrackingStart(activity, "warm_4")
                Log.w("AppStartupTypeManager2", "scenario 4")
            }
        }

        isFirstActivityForProcess = false
        isChangingConfigurations = false
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityDestroyed $activity")

        resumedActivities.removeIf { it.get() === activity }

        numberOfActivities--
        if (numberOfActivities == 0) {
            isChangingConfigurations = activity.isChangingConfigurations
        }
    }

    override fun onActivityPaused(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityPaused $activity")
    }

    override fun onActivityResumed(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityResumed $activity")

        resumedActivities.removeIf { it.get() === activity }
        resumedActivities.add(WeakReference(activity))
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.w("AppStartupTypeManager2", "onActivitySaveInstanceState $activity")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityStarted(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityStarted $activity")

        if (isInBackground) {
            if (resumedActivities.any { it.get() === activity }) {
                appStartType = AppStartType.HOT
                startTrackingStart(activity, "hot")
                Log.w("AppStartupTypeManager2", "scenario 5")
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityStopped $activity")
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun startTrackingStart(activity: Activity, type: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            subscribeToTTIDVitals(activity, type)
        }
        waitingForStart.updateAndGet { type }
        startTrackingTTFD(activity)
        startTrackingWithNewApi(type)
        subscribeToFirstDrawFinished(handler, activity) {
            reportVitalNanos(System.nanoTime(), "${type}_handler_post_ttid")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            subscribeToFirstDrawFinishedFrameCallback(handler, activity) {
                reportVitalNanos(System.nanoTime(), "${type}_commit_callback_ttid")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun startTrackingTTFD(activity: Activity) {
        handler.postDelayed({
            activity.reportFullyDrawn()
        }, 2000)

        (activity as? androidx.activity.ComponentActivity)?.let { componentActivity ->
            componentActivity.fullyDrawnReporter.addOnReportDrawnListener {
                reportVitalNanos(System.nanoTime(), "TTFD_VITAL")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun startTrackingWithNewApi(type: String) {
        appInfoRepo.subscribe {
            it.timestamps.firstFrame?.let { firstFrame ->
                reportVitalNanos(System.nanoTime(), "${it.startType.name}_${type}_new_api_ttid")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun subscribeToTTIDVitals(activity: Activity, prefix: String) {
        subscribeToTTID(handler, activity) { info ->
            reportVitalNanos(info.intendedVsyncNanos + info.totalDurationNanos, "${prefix}_frame_ttid_intended_vsync")
            reportVitalNanos(info.vsyncTimeStampNanos + info.totalDurationNanos, "${prefix}_frame_ttid_vsync")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onFrame(volatileFrameData: FrameData) {
        waitingForStart.updateAndGet { isWaiting ->
            if (isWaiting != null) {
                reportVitalNanos(volatileFrameData.frameStartNanos + volatileFrameData.frameDurationUiNanos, "${isWaiting}_jankstats_ttid")
                reportVitalNanos(volatileFrameData.frameStartNanos, "${isWaiting}_jankstats_frame_start")
            }
            null
        }
    }

    override fun onFrameMetricsData(data: FrameMetricsData) {

    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun reportVitalNanos(endNs: Long, name: String) {
        val wallNow = System.currentTimeMillis()
        val start = Process.getStartUptimeMillis()
        val now = SystemClock.uptimeMillis()
        val startDurationMs = now - start

        val durationMillis = ((endNs).nanoseconds - start.milliseconds).inWholeMilliseconds

        val realDuration = when (appStartType) {
            AppStartType.COLD -> durationMillis
            AppStartType.WARM -> (endNs.nanoseconds - initialPointNanos.nanoseconds).inWholeMilliseconds
            AppStartType.HOT -> (endNs.nanoseconds - initialPointNanos.nanoseconds).inWholeMilliseconds
            null -> 0
        }

        Log.w("TTID_LOGGING", "$name $realDuration")

        GlobalRumMonitor.get(sdkCore).sendDurationVital(
            startMs = wallNow - startDurationMs,
            durationMs = durationMillis,
            name = name
        )
    }
}
