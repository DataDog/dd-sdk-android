/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Activity
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference

internal class DatadogGesturesTracker(
    internal val targetAttributesProviders: Array<ViewAttributesProvider>
) :
    GesturesTracker {

    // region GesturesTracker
    override fun startTracking(activity: Activity) {
        // Even though the decorView is marked as NonNul I've seen cases where this was null.
        // Better to be safe here.
        val window = activity.window
        @Suppress("SENSELESS_COMPARISON")
        if (window == null || window.decorView == null) {
            return
        }

        val toWrap = window.callback ?: NoOpWindowCallback()
        // we cannot reuse a GestureDetector as we can have multiple activities
        // running in the same time
        window.callback = WindowCallbackWrapper(
            toWrap,
            generateGestureDetector(activity)
        )
    }

    override fun stopTracking(activity: Activity) {
        val currentCallback = activity.window.callback
        if (currentCallback is WindowCallbackWrapper) {
            if (currentCallback.wrappedCallback !is NoOpWindowCallback) {
                activity.window.callback = currentCallback.wrappedCallback
            } else {
                activity.window.callback = null
            }
        }
    }

    // endregion

    // region Internal

    internal fun generateGestureDetector(activity: Activity) =
        GestureDetectorCompat(
            activity,
            DatadogGesturesListener(
                WeakReference(activity.window.decorView),
                targetAttributesProviders
            )
        )

    // endregion
}
