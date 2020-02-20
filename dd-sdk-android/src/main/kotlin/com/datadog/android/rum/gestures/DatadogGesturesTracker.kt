package com.datadog.android.rum.gestures

import android.app.Activity
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.tracing.Tracer
import java.lang.ref.WeakReference

internal class DatadogGesturesTracker(val rumTracer: Tracer) :
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
                rumTracer,
                WeakReference(activity.window.decorView)
            )
        )

    // endregion
}
