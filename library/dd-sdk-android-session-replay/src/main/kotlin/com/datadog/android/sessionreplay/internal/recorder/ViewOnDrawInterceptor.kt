/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.sessionreplay.internal.processor.Processor
import com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
import java.util.WeakHashMap

internal class ViewOnDrawInterceptor(
    private val processor: Processor,
    private val snapshotProducer: SnapshotProducer
) {
    internal val decorOnDrawListeners: WeakHashMap<View, ViewTreeObserver.OnDrawListener> =
        WeakHashMap()

    fun intercept(decorViews: List<View>, ownerActivity: Activity) {
        stopInterceptingAndRemove(decorViews)
        val onDrawListener = WindowsOnDrawListener(
            ownerActivity,
            decorViews,
            processor,
            snapshotProducer
        )
        decorViews.forEach { decorView ->
            decorOnDrawListeners[decorView] = onDrawListener
            decorView.viewTreeObserver?.addOnDrawListener(onDrawListener)
        }
    }

    fun stopIntercepting(decorViews: List<View>) {
        stopInterceptingAndRemove(decorViews)
    }

    fun stopIntercepting() {
        decorOnDrawListeners.entries.forEach {
            it.key.viewTreeObserver.removeOnDrawListener(it.value)
        }
        decorOnDrawListeners.clear()
    }

    private fun stopInterceptingAndRemove(decorViews: List<View>) {
        decorViews.forEach { window ->
            decorOnDrawListeners.remove(window)?.let {
                window.viewTreeObserver.removeOnDrawListener(it)
            }
        }
    }
}
