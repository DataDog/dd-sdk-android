/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.overlay

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

/**
 * Manages the lifecycle of an [Overlay] by attaching it to activities as they are resumed.
 * With this manager, the [Overlay] instance is a singleton that is created when [start] is called
 * and destroyed when [stop] is called.
 *
 * @param application The application instance to register the lifecycle callbacks.
 * @param overlayFactory A factory function to create the [Overlay] instance.
 */
class OverlayManager(
    private val application: Application,
    private val overlayFactory: (Context) -> Overlay
) : Application.ActivityLifecycleCallbacks {

    private var singletonOverlay: Overlay? = null

    /**
     * This should be called to start managing the overlay lifecycle. It will create the singleton
     * [Overlay] instance and register for activity lifecycle callbacks.
     */
    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        singletonOverlay = overlayFactory(application)
    }

    /**
     * This should be called to stop managing the overlay lifecycle. It will unregister from
     * activity lifecycle callbacks and destroy the singleton [Overlay] instance.
     */
    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        singletonOverlay?.destroy()
        singletonOverlay = null
    }

    override fun onActivityResumed(activity: Activity) {
        singletonOverlay?.attach(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {
        // Run destroy here
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}
