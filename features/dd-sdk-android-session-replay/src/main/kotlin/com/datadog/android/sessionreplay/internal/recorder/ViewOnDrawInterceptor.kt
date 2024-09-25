/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewTreeObserver.OnDrawListener
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import java.util.WeakHashMap

internal class ViewOnDrawInterceptor(
    private val internalLogger: InternalLogger,
    private val onDrawListenerProducer: OnDrawListenerProducer
) {
    internal val decorOnDrawListeners: WeakHashMap<View, OnDrawListener> =
        WeakHashMap()

    fun intercept(
        decorViews: List<View>,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy
    ) {
        stopInterceptingAndRemove(decorViews)
        val onDrawListener =
            onDrawListenerProducer.create(decorViews, textAndInputPrivacy, imagePrivacy)
        decorViews.forEach { decorView ->
            val viewTreeObserver = decorView.viewTreeObserver
            if (viewTreeObserver != null && viewTreeObserver.isAlive) {
                try {
                    viewTreeObserver.addOnDrawListener(onDrawListener)
                    decorOnDrawListeners[decorView] = onDrawListener
                } catch (e: IllegalStateException) {
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.TELEMETRY,
                        { "Unable to add onDrawListener onto viewTreeObserver" },
                        e
                    )
                }
            }
        }

        // force onDraw here in order to make sure we take at least one snapshot if the
        // window is changed very fast
        onDrawListener.onDraw()
    }

    fun stopIntercepting(decorViews: List<View>) {
        stopInterceptingAndRemove(decorViews)
    }

    fun stopIntercepting() {
        decorOnDrawListeners.entries.forEach { (decorView, listener) ->
            stopInterceptingSafe(decorView, listener)
        }
        decorOnDrawListeners.clear()
    }

    private fun stopInterceptingAndRemove(decorViews: List<View>) {
        decorViews.forEach { decorView ->
            decorOnDrawListeners.remove(decorView)?.let { listener ->
                stopInterceptingSafe(decorView, listener)
            }
        }
    }

    private fun stopInterceptingSafe(decorView: View, listener: OnDrawListener) {
        if (decorView.viewTreeObserver.isAlive) {
            try {
                decorView.viewTreeObserver.removeOnDrawListener(listener)
            } catch (e: IllegalStateException) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { "Unable to remove onDrawListener from viewTreeObserver" },
                    e
                )
            }
        }
    }
}
