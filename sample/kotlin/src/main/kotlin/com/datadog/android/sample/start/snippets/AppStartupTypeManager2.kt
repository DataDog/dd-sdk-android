/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.start.snippets

import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.DdRumContentProvider
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

private val WARM_START_GAP_THRESHOLD = 1.minutes

class AppStartupTypeManager2(
    private val context: Context,
    private val sdkCore: InternalSdkCore,
): Application.ActivityLifecycleCallbacks {

    private var numberOfActivities: Int = 0
    private var isChangingConfigurations: Boolean = false
    private var isFirstActivityForProcess = true

    init {
        (context as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        numberOfActivities++
        if (numberOfActivities == 1 && !isChangingConfigurations) {
            val processStartedInForeground = DdRumContentProvider.processImportance == IMPORTANCE_FOREGROUND
            val now = System.nanoTime()
            val gap = (now - sdkCore.appStartTimeNs).nanoseconds

            if (isFirstActivityForProcess) {
                if (!processStartedInForeground || gap > WARM_START_GAP_THRESHOLD) {
                    Log.w("AppStartupTypeManager2", "WARM start")
                } else {
                    Log.w("AppStartupTypeManager2", "COLD start")
                }
            } else {
                Log.w("AppStartupTypeManager2", "WARM start")
            }
        }

        isFirstActivityForProcess = false
        isChangingConfigurations = false
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        numberOfActivities--
        if (numberOfActivities == 0) {
            isChangingConfigurations = activity.isChangingConfigurations
        }
    }

    override fun onActivityPaused(activity: Activity) {
        
    }

    override fun onActivityResumed(activity: Activity) {
        
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        
    }

    override fun onActivityStarted(activity: Activity) {
        val intent = activity.intent
    }

    override fun onActivityStopped(activity: Activity) {
        
    }
}
