/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver.OnDrawListener
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
import java.util.WeakHashMap

internal class ViewOnDrawInterceptor(
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val snapshotProducer: SnapshotProducer,
    private val onDrawListenerProducer: (Context, List<View>) -> OnDrawListener =
        { activity, decorViews ->
            WindowsOnDrawListener(
                activity,
                decorViews,
                recordedDataQueueHandler,
                snapshotProducer
            )
        }
) {
    internal val decorOnDrawListeners: WeakHashMap<View, OnDrawListener> =
        WeakHashMap()

    fun intercept(decorViews: List<View>, appContext: Context) {
        stopInterceptingAndRemove(decorViews)
        val onDrawListener = onDrawListenerProducer(appContext, decorViews)
        decorViews.forEach { decorView ->
            decorOnDrawListeners[decorView] = onDrawListener
            decorView.viewTreeObserver?.addOnDrawListener(onDrawListener)
        }
        // force onDraw here in order to make sure we take at least one snapshot if the
        // window is changed very fast
        onDrawListener.onDraw()
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
