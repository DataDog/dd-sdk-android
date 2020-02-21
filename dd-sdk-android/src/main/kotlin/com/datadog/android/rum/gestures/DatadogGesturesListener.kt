package com.datadog.android.rum.gestures

import android.content.res.Resources
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.tracing.Tracer
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.concurrent.TimeUnit

internal class DatadogGesturesListener(
    private val rumTracer: Tracer,
    private val decorViewReference: WeakReference<View>
) :
    GestureDetector.OnGestureListener {

    private val coordinatesContainer = IntArray(2)

    // region GesturesListener

    override fun onShowPress(e: MotionEvent) {
        // No Op
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val decorView: View? = decorViewReference.get()
        handleTapUpFor(decorView, e)
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        // No Op
        // we just intercept but not steal the event
        return false
    }

    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        // No Op
        // we just intercept but not steal the event
        return false
    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        // No Op
        // we just intercept but not steal the event
        return false
    }

    override fun onLongPress(e: MotionEvent) {
        // No Op
    }

    // endregion

    // region Internal

    private fun handleTapUpFor(decorView: View?, e: MotionEvent) {
        if (decorView != null) {
            findTarget(decorView, e.x, e.y)?.let { target ->
                // we just intercept but not steal the event
                val spanBuilder = rumTracer
                    .buildSpan(UI_TAP_ACTION_EVENT)
                spanBuilder.withTag(TAG_TARGET_CLASS_NAME, target.javaClass.canonicalName)
                val targetId: String = resolveTargetIdOrResourceName(target)
                spanBuilder.withTag(
                    TAG_TARGET_RESOURCE_ID,
                    targetId
                )

                spanBuilder
                    .start()
                    .finish(DEFAULT_EVENT_DURATION)
            }
        }
    }

    private fun resolveTargetIdOrResourceName(target: View): String {
        return try {
            target.resources.getResourceEntryName(target.id) ?: targetIdAsHexa(target)
        } catch (e: Resources.NotFoundException) {
            sdkLogger.e("$TAG: Could not find resource name for target:${target.id}", e)
            targetIdAsHexa(target)
        }
    }

    private fun targetIdAsHexa(target: View) = "0x${target.id.toString(16)}"

    private fun findTarget(decorView: View, x: Float, y: Float): View? {
        val stack = LinkedList<View>()
        stack.addFirst(decorView)

        while (stack.isNotEmpty()) {
            val view = stack.removeFirst()

            if (isValidTarget(view)) {
                return view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, stack, coordinatesContainer)
            }
        }

        devLogger.i(
            "DatadogGesturesTracker: We could not find a valid target for the TapEvent. " +
                    "The DecorView was empty and either transparent or not clickable " +
                    "for this Activity"
        )
        return null
    }

    private fun handleViewGroup(
        view: ViewGroup,
        x: Float,
        y: Float,
        stack: LinkedList<View>,
        coordinatesContainer: IntArray
    ) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (hitTest(child, x, y, coordinatesContainer)) {
                stack.add(child)
            }
        }
    }

    private fun isValidTarget(view: View): Boolean {
        if (!(view.isClickable && view.visibility == View.VISIBLE)) {
            return false
        }
        if (view is ViewGroup) {
            return (view.childCount == 0)
        }
        return true
    }

    private fun hitTest(
        view: View,
        x: Float,
        y: Float,
        container: IntArray
    ): Boolean {
        view.getLocationOnScreen(container)
        val vx = container[0]
        val vy = container[1]
        val w = view.width
        val h = view.height

        return !(x < vx || x > vx + w || y < vy || y > vy + h)
    }

    // endregion

    companion object {
        private const val TAG = "DatadogGestureListener"
        internal const val UI_TAP_ACTION_EVENT = "TapEvent"
        internal const val TAG_TARGET_CLASS_NAME = "target.classname"
        internal const val TAG_TARGET_RESOURCE_ID = "target.resourceId"
        val DEFAULT_EVENT_DURATION = TimeUnit.MILLISECONDS.toMicros(16)
    }
}
