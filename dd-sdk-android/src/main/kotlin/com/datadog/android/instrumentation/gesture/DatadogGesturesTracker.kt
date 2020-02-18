package com.datadog.android.instrumentation.gesture

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.tracing.Tracer
import java.util.concurrent.TimeUnit

internal class DatadogGesturesTracker(val rumTracer: Tracer) : GesturesTracker,
    GestureDetector.OnGestureListener {

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

    // region GesturesListener

    override fun onShowPress(e: MotionEvent?) {
        // No Op
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        // TODO RUMM-236 Add more details here
        // we just intercept but not steal the event
        rumTracer
            .buildSpan(UI_TAP_ACTION_EVENT)
            .start()
            .finish(DEFAULT_EVENT_DURATION)
        return false
    }

    override fun onDown(e: MotionEvent?): Boolean {
        // No Op
        // we just intercept but not steal the event
        return false
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        // No Op
        // we just intercept but not steal the event
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        // No Op
        // we just intercept but not steal the event
        return false
    }

    override fun onLongPress(e: MotionEvent?) {
        // No Op
    }

    // endregion

    // region Internal

    internal fun generateGestureDetector(activity: Activity) =
        GestureDetectorCompat(activity, this)

    // endregion

    companion object {
        internal const val UI_TAP_ACTION_EVENT = "TapEvent"
        val DEFAULT_EVENT_DURATION = TimeUnit.MILLISECONDS.toMicros(16)
    }
}
