package com.datadog.android.rum.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.tracing.AndroidTracer
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.concurrent.TimeUnit

internal class DatadogGesturesListener(
    private val rumTracer: AndroidTracer,
    private val decorViewReference: WeakReference<View>
) :
    GestureDetector.OnGestureListener {

    // region GesturesListener

    override fun onShowPress(e: MotionEvent) {
        // No Op
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        decorViewReference.get()?.let {
            // we just intercept but not steal the event
            val spanBuilder = rumTracer
                .buildSpan(UI_TAP_ACTION_EVENT)
            val target =
                findTarget(
                    it,
                    e.x,
                    e.y
                )
            spanBuilder.withTag(TAG_TARGET_CLASS_NAME, target.javaClass.canonicalName)
            // we will handle general exceptions here. We don't know what might go wrong apart from
            // ResourceNotFound exception.
            @Suppress("TooGenericExceptionCaught")
            try {
                spanBuilder.withTag(
                    TAG_TARGET_RESOURCE_ID,
                    target.resources.getResourceEntryName(target.id)
                )
            } catch (e: Exception) {
                sdkLogger.e("$TAG: Could not found resource name for target:${target.id}", e)
            }
            spanBuilder
                .start()
                .finish(DEFAULT_EVENT_DURATION)
        }
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

    companion object {
        private const val TAG = "DatadogGestureListener"
        internal const val UI_TAP_ACTION_EVENT = "TapEvent"
        internal const val TAG_TARGET_CLASS_NAME = "target.classname"
        internal const val TAG_TARGET_RESOURCE_ID = "target.resourceId"
        val DEFAULT_EVENT_DURATION = TimeUnit.MILLISECONDS.toMicros(16)

        private fun findTarget(decorView: View, x: Float, y: Float): View {
            val stack = LinkedList<View>()
            stack.addFirst(decorView)
            while (stack.isNotEmpty()) {
                val view = stack.removeFirst()

                if (isValidTarget(view)) {
                    return view
                }

                if (view is ViewGroup) {
                    handleViewGroup(view, x, y, stack)
                }
            }

            return decorView
        }

        private fun handleViewGroup(
            view: ViewGroup,
            x: Float,
            y: Float,
            stack: LinkedList<View>
        ) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (hitTest(child, x, y)) {
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

        private fun hitTest(view: View, x: Float, y: Float): Boolean {
            val l = IntArray(2)
            view.getLocationOnScreen(l)
            val vx = l[0]
            val vy = l[1]
            val w = view.width
            val h = view.height

            return !(x < vx || x > vx + w || y < vy || y > vy + h)
        }
    }
}
