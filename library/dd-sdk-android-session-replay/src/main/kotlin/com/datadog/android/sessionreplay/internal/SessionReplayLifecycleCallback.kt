/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Activity
import android.os.Bundle
import android.view.Window
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentActivity
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderFragmentLifecycleCallback
import java.util.WeakHashMap

internal class SessionReplayLifecycleCallback(
    private val onWindowRefreshedCallback: OnWindowRefreshedCallback
) : LifecycleCallback, OnWindowRefreshedCallback {

    private val currentActiveWindows = WeakHashMap<Window, Any?>()

    override fun onWindowsAdded(windows: List<Window>) {
        windows.forEach {
            currentActiveWindows[it] = null
        }
        onWindowRefreshedCallback.onWindowsAdded(windows)
    }

    override fun onWindowsRemoved(windows: List<Window>) {
        windows.forEach {
            currentActiveWindows.remove(it)
        }
        onWindowRefreshedCallback.onWindowsRemoved(windows)
    }

    override fun getCurrentWindows(): List<Window> {
        return currentActiveWindows.keys.toList()
    }

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is FragmentActivity) {
            // we need to register before the activity resumes to catch all the fragments
            // added even before the activity resumes
            val lifecycleCallback = RecorderFragmentLifecycleCallback(this)
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                lifecycleCallback,
                true
            )
        }
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        activity.window?.let {
            currentActiveWindows[it] = null
            onWindowRefreshedCallback.onWindowsAdded(listOf(it))
        }
    }

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        activity.window?.let {
            currentActiveWindows.remove(it)
            onWindowRefreshedCallback.onWindowsRemoved(listOf(it))
        }
    }

    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }
}
