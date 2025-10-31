/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.insights.internal.DefaultInsightsCollector
import com.datadog.android.rum.ExperimentalRumApi

@ExperimentalRumApi
internal object OverlayManager : Application.ActivityLifecycleCallbacks {

    private var app: Application? = null
    private var overlay: DefaultInsightsOverlay? = null
    private lateinit var collector: DefaultInsightsCollector

    fun start(application: Application, insightsCollector: DefaultInsightsCollector) {
        app = application
        collector = insightsCollector
        application.registerActivityLifecycleCallbacks(this)
    }

    fun stop() {
        val a = app ?: return
        a.unregisterActivityLifecycleCallbacks(this)
        overlay?.destroy()
        overlay = null
        app = null
    }

    override fun onActivityResumed(activity: Activity) {
        overlay?.destroy()
        overlay = DefaultInsightsOverlay(collector).also { it.attach(activity) }
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
}
