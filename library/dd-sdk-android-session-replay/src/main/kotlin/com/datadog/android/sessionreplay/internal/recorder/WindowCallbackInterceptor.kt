/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Activity
import android.view.Window
import com.datadog.android.sessionreplay.internal.processor.Processor
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderWindowCallback
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import java.util.WeakHashMap

internal class WindowCallbackInterceptor(
    private val processor: Processor,
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor,
    private val timeProvider: TimeProvider
) {
    internal val wrappedWindows: WeakHashMap<Window, Any?> = WeakHashMap()

    fun intercept(windows: List<Window>, ownerActivity: Activity) {
        windows.forEach { window ->
            wrapWindowCallback(window, ownerActivity)
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
        wrappedWindows.entries.forEach {
            unwrapWindowCallback(it.key)
        }
        wrappedWindows.clear()
    }

    private fun wrapWindowCallback(window: Window, ownerActivity: Activity) {
        val toWrap = window.callback ?: NoOpWindowCallback()
        window.callback = RecorderWindowCallback(
            processor,
            toWrap,
            timeProvider,
            viewOnDrawInterceptor,
            ownerActivity
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
