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
import java.util.IdentityHashMap

@ExperimentalRumApi
internal class OverlayManager(
    application: Application,
    private val collector: DefaultInsightsCollector
) : Application.ActivityLifecycleCallbacks {

    private val overlays = IdentityHashMap<Activity, DefaultInsightsOverlay>()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        overlays.remove(activity)?.destroy()

        val overlay = DefaultInsightsOverlay(collector).also { it.attach(activity) }
        overlays[activity] = overlay
    }

    override fun onActivityPaused(activity: Activity) {
        overlays.remove(activity)?.destroy()
    }

    override fun onActivityDestroyed(activity: Activity) {
        overlays.remove(activity)?.destroy()
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
}
