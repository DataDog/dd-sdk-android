/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.app.Activity
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.sessionreplay.processor.Processor
import com.datadog.android.sessionreplay.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.recorder.callback.RecorderWindowCallback
import com.datadog.android.sessionreplay.recorder.listener.WindowsOnDrawListener
import com.datadog.android.sessionreplay.utils.TimeProvider
import java.util.WeakHashMap

internal class ScreenRecorder(
    private val processor: Processor,
    private val snapshotProducer: SnapshotProducer,
    private val timeProvider: TimeProvider
) : Recorder {
    internal val windowsListeners: WeakHashMap<Window, ViewTreeObserver.OnDrawListener> =
        WeakHashMap()

    override fun startRecording(windows: List<Window>, ownerActivity: Activity) {
        // first we make sure we don't record a window multiple times
        stopRecordingAndRemove(windows)
        val screenDensity = ownerActivity.resources.displayMetrics.density
        val onDrawListener = WindowsOnDrawListener(
            ownerActivity,
            windows,
            screenDensity,
            processor,
            snapshotProducer
        )
        windows.forEach { window ->
            val decorView = window.decorView
            windowsListeners[window] = onDrawListener
            decorView.viewTreeObserver?.addOnDrawListener(onDrawListener)
            wrapWindowCallback(window, screenDensity)
        }
    }

    override fun stopRecording(windows: List<Window>) {
        stopRecordingAndRemove(windows)
    }

    override fun stopRecording() {
        windowsListeners.entries.forEach {
            it.key.decorView.viewTreeObserver.removeOnDrawListener(it.value)
            unwrapWindowCallback(it.key)
        }
        windowsListeners.clear()
    }

    private fun stopRecordingAndRemove(windows: List<Window>) {
        windows.forEach { window ->
            windowsListeners.remove(window)?.let {
                window.decorView.viewTreeObserver.removeOnDrawListener(it)
            }
            unwrapWindowCallback(window)
        }
    }

    private fun wrapWindowCallback(window: Window, screenDensity: Float) {
        val toWrap = window.callback ?: NoOpWindowCallback()
        window.callback = RecorderWindowCallback(
            processor,
            screenDensity,
            toWrap,
            timeProvider
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
