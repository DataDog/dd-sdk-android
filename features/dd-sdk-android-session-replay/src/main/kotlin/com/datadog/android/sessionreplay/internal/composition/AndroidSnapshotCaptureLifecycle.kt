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
import com.datadog.android.sessionreplay.internal.recorder.WindowReflectionUtils
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback

internal class AndroidSnapshotCaptureLifecycle(
    private val application: Application,
    private val interceptor: CompositionViewOnDrawInterceptor,
    private val touchInterceptor: CompositionWindowTouchInterceptor,
    private val internalLogger: InternalLogger,
    currentActivity: Activity? = null,
    private val uiHandler: Handler = Handler(Looper.getMainLooper()),
    private val windowProvider: () -> List<View> = {
        WindowInspector.getGlobalWindowViews(internalLogger)
    },
    private val windowFromDecorView: (View) -> Window? = {
        WindowReflectionUtils.getWindowFromDecorView(it, internalLogger)
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
            refreshInterceptors()
            scheduleUntrackedWindowRefresh()
        }
    }

    @Suppress("ThreadSafety") // Handler posts this block onto the main looper.
    override fun stop() {
        uiHandler.post {
            isRunning = false
            interceptor.stop()
            touchInterceptor.stop()
        }
    }

    @MainThread
    override fun onWindowsAdded(windows: List<Window>) = refreshWindows()

    @MainThread
    override fun onWindowsRemoved(windows: List<Window>) = refreshWindows()

    @MainThread
    private fun refreshWindows() {
        if (isRunning) refreshInterceptors()
    }

    @MainThread
    private fun refreshInterceptors() {
        val resolved = resolveWindows(lifecycleCallback.getCurrentWindows())
        interceptor.intercept(resolved.decorViews)
        touchInterceptor.intercept(resolved.windows)
    }

    /**
     * ActivityThread adds an activity's decor view to the window manager *after* dispatching
     * onActivityResumed, so at the moment this callback runs the window manager does not know about
     * the window yet and [windowProvider] alone reports nothing. The tracked windows are the
     * authoritative source for activity windows; the window manager still contributes the ones no
     * lifecycle callback reports, such as dialogs and popups. [windowFromDecorView] resolves those
     * untracked decor views back to a [Window], which touch interception needs but draw
     * interception does not.
     */
    @MainThread
    private fun resolveWindows(trackedWindows: List<Window>): ResolvedWindows {
        val trackedDecorViews = trackedWindows.mapNotNull { it.peekDecorView() }
        val untrackedDecorViews = windowProvider().filterNot(trackedDecorViews::contains)
        val untrackedWindows = untrackedDecorViews.mapNotNull(windowFromDecorView)
        return ResolvedWindows(
            decorViews = trackedDecorViews + untrackedDecorViews,
            windows = trackedWindows + untrackedWindows
        )
    }

    /**
     * A plain Dialog or PopupWindow is neither an activity nor a DialogFragment, so nothing calls
     * [refreshWindows] when one is shown or dismissed while the host activity stays resumed.
     * Re-polling [windowProvider] on a fixed cadence is what still picks those up, bounding how
     * long such a window can be missing from (or stale in) the intercepted set.
     */
    @Suppress("ThreadSafety") // Handler posts this block onto the main looper.
    @MainThread
    private fun scheduleUntrackedWindowRefresh() {
        uiHandler.postDelayed(
            {
                if (isRunning) {
                    refreshInterceptors()
                    scheduleUntrackedWindowRefresh()
                }
            },
            UNTRACKED_WINDOW_REFRESH_INTERVAL_MS
        )
    }

    private data class ResolvedWindows(val decorViews: List<View>, val windows: List<Window>)

    private companion object {
        const val UNTRACKED_WINDOW_REFRESH_INTERVAL_MS = 1_000L
    }
}
