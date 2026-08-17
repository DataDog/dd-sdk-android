/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback

internal interface CompositionCaptureLifecycle {
    fun registerCallbacks()
    fun unregisterCallbacks()
    fun start()
    fun stop()
}

internal class AndroidSnapshotCaptureLifecycle(
    private val application: Application,
    private val interceptor: CompositionViewOnDrawInterceptor,
    private val internalLogger: InternalLogger,
    currentActivity: Activity? = null,
    private val uiHandler: Handler = Handler(Looper.getMainLooper()),
    private val windowProvider: () -> List<View> = {
        WindowInspector.getGlobalWindowViews(internalLogger)
    }
) : CompositionCaptureLifecycle, OnWindowRefreshedCallback {
    private val lifecycleCallback = SessionReplayLifecycleCallback(this)
    private var isRunning = false

    init {
        currentActivity?.let {
            lifecycleCallback.setCurrentWindow(it)
            lifecycleCallback.registerFragmentLifecycleCallbacks(it)
        }
    }

    override fun registerCallbacks() {
        application.registerActivityLifecycleCallbacks(lifecycleCallback)
    }

    override fun unregisterCallbacks() {
        application.unregisterActivityLifecycleCallbacks(lifecycleCallback)
    }

    @Suppress("ThreadSafety") // Handler posts this block onto the main looper.
    override fun start() {
        uiHandler.post {
            isRunning = true
            interceptor.intercept(decorViewsOf(lifecycleCallback.getCurrentWindows()))
        }
    }

    @Suppress("ThreadSafety") // Handler posts this block onto the main looper.
    override fun stop() {
        uiHandler.post {
            isRunning = false
            interceptor.stop()
        }
    }

    @MainThread
    override fun onWindowsAdded(windows: List<Window>) = refreshWindows()

    @MainThread
    override fun onWindowsRemoved(windows: List<Window>) = refreshWindows()

    @MainThread
    private fun refreshWindows() {
        if (isRunning) interceptor.intercept(decorViewsOf(lifecycleCallback.getCurrentWindows()))
    }

    /**
     * ActivityThread adds an activity's decor view to the window manager *after* dispatching
     * onActivityResumed, so at the moment this callback runs the window manager does not know about
     * the window yet and [windowProvider] alone reports nothing. The tracked windows are the
     * authoritative source for activity windows; the window manager still contributes the ones no
     * lifecycle callback reports, such as dialogs and popups.
     */
    @MainThread
    private fun decorViewsOf(windows: List<Window>): List<View> {
        val trackedDecorViews = windows.mapNotNull { it.peekDecorView() }
        val untrackedDecorViews = windowProvider().filterNot(trackedDecorViews::contains)
        return trackedDecorViews + untrackedDecorViews
    }
}
