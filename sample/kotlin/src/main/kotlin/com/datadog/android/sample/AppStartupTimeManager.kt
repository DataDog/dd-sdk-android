/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.datadog.android.api.SdkCore
import com.datadog.android.core.InternalSdkCore
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.TimeUnit

class AppStartupTypeManager(
    private val context: Context,
    private val tracer: Tracer,
    private val sdkCore: SdkCore,
): Application.ActivityLifecycleCallbacks {

    private val handler = Handler(Looper.getMainLooper())

    private val processStartTimeNs: Long by lazy {
        (sdkCore as InternalSdkCore).appStartTimeNs
    }

    private var appStartupSpan: Span = tracer.spanBuilder("app_startup")
        .apply { setStartTimestamp(processStartTimeNs, TimeUnit.NANOSECONDS) }
        .startSpan()

    private var activityCreatedStartTime: Long = 0
    private var activityStartedStartTime: Long = 0

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityCreatedStartTime = System.nanoTime()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        val span = tracer.spanBuilder("Activity.onCreate")
            .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
            .setStartTimestamp(relativeTime(activityCreatedStartTime), TimeUnit.NANOSECONDS)
            .startSpan()

        span.end(relativeNow(), TimeUnit.NANOSECONDS)
    }

    override fun onActivityDestroyed(activity: Activity) {
        
    }

    override fun onActivityPaused(activity: Activity) {
        
    }

    override fun onActivityResumed(activity: Activity) {
        
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        
    }

    override fun onActivityStarted(activity: Activity) {
        
    }

    override fun onActivityPreStarted(activity: Activity) {
        activityStartedStartTime = System.nanoTime()
    }

    override fun onActivityPostStarted(activity: Activity) {
        val span = tracer.spanBuilder("Activity.onStart")
            .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
            .setStartTimestamp(relativeTime(activityStartedStartTime), TimeUnit.NANOSECONDS)
            .startSpan()

        val now = relativeNow()
        Log.w("WAHAHA", "app_startup: $now, process_start: $processStartTimeNs")
        span.end(now, TimeUnit.NANOSECONDS)
        handler.postDelayed({appStartupSpan.end(relativeNow(), TimeUnit.NANOSECONDS)}, 5000)

    }

    override fun onActivityStopped(activity: Activity) {
        
    }

    private fun relativeTime(time: Long): Long = time

    private fun relativeNow(): Long = relativeTime(System.nanoTime())
}
