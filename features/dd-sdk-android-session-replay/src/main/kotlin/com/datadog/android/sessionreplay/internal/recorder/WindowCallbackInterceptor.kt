/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.Context
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderWindowCallback
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.util.WeakHashMap

internal class WindowCallbackInterceptor(
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor,
    private val timeProvider: TimeProvider,
    private val rumContextProvider: RumContextProvider,
    private val internalLogger: InternalLogger,
    private val imagePrivacy: ImagePrivacy,
    private val textAndInputPrivacy: TextAndInputPrivacy,
    private val touchPrivacyManager: TouchPrivacyManager
) {
    private val wrappedWindows: WeakHashMap<Window, Any?> = WeakHashMap()

    // guards RecorderWindowCallback's deferred (posted) installation of callbacks on newly
    // discovered dialog windows: without this, a dialog detected right before stopIntercepting()
    // could still get wrapped after we've stopped, and would never be torn down afterwards.
    private var isStopped = false

    fun intercept(windows: List<Window>, appContext: Context) {
        isStopped = false
        windows.forEach { window ->
            wrapWindowCallback(window, appContext)
            wrappedWindows[window] = null
        }
    }

    fun stopIntercepting(windows: List<Window>) {
        windows.forEach {
            unwrapWindowCallback(it)
            wrappedWindows.remove(it)
        }
    }

    fun stopIntercepting() {
        isStopped = true
        wrappedWindows.entries.forEach {
            unwrapWindowCallback(it.key)
        }
        wrappedWindows.clear()
    }

    // a RecorderWindowCallback can discover and wrap windows on its own (e.g. dialogs, which
    // aren't reported through the Activity lifecycle) — this lets it register those windows here
    // so they get torn down by stopIntercepting() like any other tracked window.
    internal fun trackWrappedWindow(window: Window) {
        wrappedWindows[window] = null
    }

    private fun wrapWindowCallback(window: Window, appContext: Context) {
        val toWrap = window.callback ?: NoOpWindowCallback()
        window.callback = RecorderWindowCallback(
            appContext,
            recordedDataQueueHandler,
            toWrap,
            timeProvider,
            rumContextProvider,
            viewOnDrawInterceptor,
            internalLogger,
            textAndInputPrivacy,
            imagePrivacy,
            touchPrivacyManager,
            onWindowWrapped = { trackWrappedWindow(it) },
            shouldInstallCallbacks = { !isStopped }
        )
    }

    private fun unwrapWindowCallback(window: Window) {
        val callback = window.callback
        if (callback is RecorderWindowCallback) {
            val wrappedCallback = callback.wrappedCallback
            if (wrappedCallback !is NoOpWindowCallback) {
                window.callback = wrappedCallback
            } else {
                window.callback = null
            }
        }
    }
}
