/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Manages the lifecycle of the insights overlay by attaching and detaching it
 * to activities based on their lifecycle events.
 *
 * @param application The application instance to register lifecycle callbacks.
 */
@Suppress("OPT_IN_USAGE")
class OverlayManager(private val application: Application) : Application.ActivityLifecycleCallbacks {

    private var overlay: DefaultInsightsOverlay? = null

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        overlay = DefaultInsightsOverlay()
    }

    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        overlay?.destroy()
        overlay = null
    }

    override fun onActivityResumed(activity: Activity) {
        overlay?.attach(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        overlay?.detach()
    }

    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}
