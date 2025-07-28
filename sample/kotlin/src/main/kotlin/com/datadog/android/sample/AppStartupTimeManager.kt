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

    private var appStartupSpan: Span = tracer.spanBuilder("app_startup").startSpan()

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
            .setStartTimestamp(activityCreatedStartTime - getProcessStartTimeNs(), TimeUnit.NANOSECONDS)
            .startSpan()

        span.end(System.nanoTime() - getProcessStartTimeNs(), TimeUnit.NANOSECONDS)
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
            .setStartTimestamp(activityStartedStartTime - getProcessStartTimeNs(), TimeUnit.NANOSECONDS)
            .startSpan()

        span.end(System.nanoTime() - getProcessStartTimeNs(), TimeUnit.NANOSECONDS)
        appStartupSpan.end(System.nanoTime() - getProcessStartTimeNs(), TimeUnit.NANOSECONDS)
    }

    override fun onActivityStopped(activity: Activity) {
        
    }

    private fun getProcessStartTimeNs(): Long {
        return (sdkCore as InternalSdkCore).appStartTimeNs
    }
}
