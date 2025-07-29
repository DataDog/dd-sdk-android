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
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class AppStartupType {
    COLD,
    WARM,
    HOT
}

class AppStartupTypeManager(
    private val context: Context,
    private val tracer: Tracer,
    private val sdkCore: SdkCore,
): Application.ActivityLifecycleCallbacks {

    private val epoch = Instant.now()

    private val handler = Handler(Looper.getMainLooper())

    private val processStartTimeNs: Long by lazy {
        (sdkCore as InternalSdkCore).appStartTimeNs
    }

    private var appStartupSpan: Span = tracer.spanBuilder("app_startup")
        .apply {
            setStartTimestamp(epoch)
        }
        .startSpan()

    private var activityCreatedStartTime: Instant = Instant.MIN
    private var activityStartedStartTime: Instant = Instant.MIN

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityPreCreated")
        activityCreatedStartTime = relativeNow()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityCreated")
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityPostCreated")
        val span = tracer.spanBuilder("Activity.onCreate")
            .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
            .setStartTimestamp(activityCreatedStartTime)
            .startSpan()

        span.end(relativeNow())
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityDestroyed")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityPaused")
    }

    override fun onActivityResumed(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityResumed")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivitySaveInstanceState")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityStarted")
    }

    override fun onActivityPreStarted(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityPreStarted")
        activityStartedStartTime = relativeNow()
    }

    override fun onActivityPostStarted(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityPostStarted")
        val span = tracer.spanBuilder("Activity.onStart")
            .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
            .setStartTimestamp(activityStartedStartTime)
            .startSpan()

        val now = relativeNow()
        Log.w("WAHAHA", "app_startup: $now, process_start: $processStartTimeNs")
        span.end(now)
        handler.postDelayed({appStartupSpan.end(relativeNow())}, 5000)

    }

    override fun onActivityStopped(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityStopped")
    }

    private fun relativeTime(time: Long): Instant = epoch.plusNanos(time - processStartTimeNs)

    private fun relativeNow(): Instant = relativeTime(System.nanoTime())

    private fun addStartupTypeAttribute(type: AppStartupType) {
        appStartupSpan.setAttribute("app_startup_type", type.name)
    }
}
