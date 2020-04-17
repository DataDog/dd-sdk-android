/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.View
import android.view.Window
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference

internal class DatadogGesturesTracker(
    internal val targetAttributesProviders: Array<ViewAttributesProvider>
) : GesturesTracker {

    // region GesturesTracker

    override fun startTracking(window: Window?, context: Context) {
        val decorView = window?.decorView

        // Even though the decorView is marked as NonNul I've seen cases where this was null.
        // Better to be safe here.
        @Suppress("SENSELESS_COMPARISON")
        if (window == null || decorView == null) {
            return
        }

        val toWrap = window.callback ?: NoOpWindowCallback()
        // we cannot reuse a GestureDetector as we can have multiple activities
        // running in the same time
        window.callback = WindowCallbackWrapper(
            toWrap,
            generateGestureDetector(context, decorView)
        )
    }

    override fun stopTracking(window: Window?, context: Context) {
        if (window == null) {
            return
        }

        val currentCallback = window.callback
        if (currentCallback is WindowCallbackWrapper) {
            if (currentCallback.wrappedCallback !is NoOpWindowCallback) {
                window.callback = currentCallback.wrappedCallback
            } else {
                window.callback = null
            }
        }
    }

    // endregion

    // region Internal

    internal fun generateGestureDetector(
        context: Context,
        decorView: View
    ): GesturesDetectorWrapper {
        return GesturesDetectorWrapper(
            context,
            GesturesListener(
                WeakReference(decorView),
                targetAttributesProviders
            )
        )
    }

    // endregion
}
