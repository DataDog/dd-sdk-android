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
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("OPT_IN_USAGE")
internal object OverlayManager : Application.ActivityLifecycleCallbacks {

    private var app: Application? = null
    private var overlay: DefaultInsightsOverlay? = null
    private val started = AtomicBoolean(false)

    fun start(application: Application, insightsCollector: DefaultInsightsCollector) {
        if (!started.compareAndSet(false, true)) return
        app = application
        overlay = DefaultInsightsOverlay(insightsCollector)
        app?.registerActivityLifecycleCallbacks(this)
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        app?.unregisterActivityLifecycleCallbacks(this)
        overlay?.destroy()
        overlay = null
        app = null
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
