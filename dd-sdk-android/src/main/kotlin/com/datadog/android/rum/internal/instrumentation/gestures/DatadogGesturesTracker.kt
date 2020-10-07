/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.Window
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference

internal class DatadogGesturesTracker(
    internal val targetAttributesProviders: Array<ViewAttributesProvider>
) : GesturesTracker {

    // region GesturesTracker

    override fun startTracking(window: Window?, context: Context) {
        @Suppress("SENSELESS_COMPARISON")
        if (window == null) {
            return
        }

        val toWrap = window.callback ?: NoOpWindowCallback()
        val gesturesDetector = generateGestureDetector(context, window)

        window.callback = WindowCallbackWrapper(toWrap, gesturesDetector)
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
        window: Window
    ): GesturesDetectorWrapper {
        return GesturesDetectorWrapper(
            context,
            GesturesListener(
                WeakReference(window),
                targetAttributesProviders
            )
        )
    }

    // endregion
}
