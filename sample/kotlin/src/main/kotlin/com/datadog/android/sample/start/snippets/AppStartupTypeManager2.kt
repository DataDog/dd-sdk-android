/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.start.snippets

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
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.GlobalRumMonitor
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private val START_GAP_THRESHOLD = 5.seconds

class AppStartupTypeManager2(
    private val context: Context,
    private val sdkCore: InternalSdkCore,
): Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess = true

    private val handler = Handler(Looper.getMainLooper())

    private var isInBackground = true

    private val resumedActivities = mutableSetOf<WeakReference<Activity>>()

    private val activityStates = mutableListOf<Pair<WeakReference<Activity>, Lifecycle.Event>>()

    private val frameListeners = mutableListOf<Pair<WeakReference<Activity>, Window.OnFrameMetricsAvailableListener>>()

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
                    Log.w("AppStartupTypeManager2", "scenario 3")
                } else {
                    Log.w("AppStartupTypeManager2", "scenario 1")
                }
            } else {
                Log.w("AppStartupTypeManager2", "scenario 4")
            }
        }

        isFirstActivityForProcess = false
        isChangingConfigurations = false
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityDestroyed $activity")
        activityStates.removeIf { it.first.get() === activity }

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
                Log.w("AppStartupTypeManager2", "scenario 5")
            }
        }

        subscribeToTTID(activity) { info ->
            val start = Process.getStartUptimeMillis()
            val now = SystemClock.uptimeMillis()
            val startDurationMs = now - start

            val newDuration = ((info.intendedVsyncNanos + info.totalDurationNanos).nanoseconds - start.milliseconds).inWholeMilliseconds

            GlobalRumMonitor.get(sdkCore).sendDurationVital(
                startMs = System.currentTimeMillis() - startDurationMs,
                durationMs = newDuration,
                name = "frame_ttid"
            )
        }
    }

    override fun onActivityStopped(activity: Activity) {
        Log.w("AppStartupTypeManager2", "onActivityStopped $activity")
    }
}

