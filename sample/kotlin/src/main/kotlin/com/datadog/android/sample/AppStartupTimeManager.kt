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
import android.view.ViewTreeObserver
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.log.LogAttributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import java.time.Instant

enum class AppStartupType {
    COLD,
    WARM,
    HOT
}

class AppStartupTypeManager(
    private val context: Context,
    private val tracer: Tracer,
    private val sdkCore: SdkCore,
): Application.ActivityLifecycleCallbacks, ViewTreeObserver.OnDrawListener {

    private val epoch = Instant.now()

    private val handler = Handler(Looper.getMainLooper())

    private var firstActivityDrawn: Boolean = false

    private var fullDisplayCalled: Boolean = false
    private var fullDisplayInstant: Instant = Instant.MIN

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
    private var activityResumeStartTime: Instant = Instant.MIN

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    fun reportFullyDisplayed() {
        if (!fullDisplayCalled) {
            fullDisplayCalled = true

            val span = tracer.spanBuilder("ttfd")
                .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
                .setStartTimestamp(epoch)
                .startSpan()

            val now = relativeNow()

            span.end(now)
        }
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (!firstActivityDrawn) {
            activity.window.decorView.viewTreeObserver.addOnDrawListener(this)
            activityCreatedStartTime = relativeNow()
        }

        Log.w("WAHAHA", "${activity::class.java.name} onActivityPreCreated")
    }

    override fun onActivityPreResumed(activity: Activity) {
        activityResumeStartTime = relativeNow()
    }

    override fun onActivityPostResumed(activity: Activity) {
        if (!firstActivityDrawn) {
            val span = tracer.spanBuilder("Activity.onResume")
                .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
                .setStartTimestamp(activityResumeStartTime)
                .startSpan()

            val now = relativeNow()
            Log.w("WAHAHA", "app_startup: $now, process_start: $processStartTimeNs")
            span.end(now)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityCreated")
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityPostCreated")
        if (!firstActivityDrawn) {
            val span = tracer.spanBuilder("Activity.onCreate")
                .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
                .setStartTimestamp(activityCreatedStartTime)
                .startSpan()

            span.end(relativeNow())
        }

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

        if (!firstActivityDrawn) {
            val span = tracer.spanBuilder("Activity.onStart")
                .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
                .setStartTimestamp(activityStartedStartTime)
                .startSpan()

            val now = relativeNow()
            Log.w("WAHAHA", "app_startup: $now, process_start: $processStartTimeNs")
            span.end(now)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        Log.w("WAHAHA", "${activity::class.java.name} onActivityStopped")
    }

    private fun relativeTime(time: Long): Instant = epoch.plusNanos(time - processStartTimeNs)

    private fun relativeNow(): Instant = relativeTime(System.nanoTime())

    private fun addStartupTypeAttribute(type: AppStartupType) {
        appStartupSpan.setAttribute("app_startup_type", type.name)
    }

    override fun onDraw() {
        if (!firstActivityDrawn) {
            firstActivityDrawn = true

            val span = tracer.spanBuilder("ttid")
                .setParent(io.opentelemetry.context.Context.current().with(appStartupSpan))
                .setStartTimestamp(epoch)
                .startSpan()

            val now = relativeNow()

            span.end(now)

            handler.postDelayed({
                reportFullyDisplayed()

                val rumContext = (sdkCore as InternalSdkCore).getFeatureContext(Feature.RUM_FEATURE_NAME)
                val appLaunchViewId = rumContext["application_launch_view_id"] as? String
                val applicationId = rumContext["application_id"] as? String
                val sessionId = rumContext["session_id"] as? String
                appStartupSpan.setAttribute(LogAttributes.RUM_APPLICATION_ID, applicationId!!)
                appStartupSpan.setAttribute(LogAttributes.RUM_SESSION_ID, sessionId!!)
                appStartupSpan.setAttribute(LogAttributes.RUM_VIEW_ID, appLaunchViewId!!)

                appStartupSpan.end(relativeNow())
            }, 2000)
        }
    }
}
