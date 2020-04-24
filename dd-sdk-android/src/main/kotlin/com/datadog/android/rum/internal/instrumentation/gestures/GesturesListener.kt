/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import androidx.core.view.ScrollingView
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference
import java.util.LinkedList
import kotlin.math.abs

internal class GesturesListener(
    private val decorViewReference: WeakReference<View>,
    private val attributesProviders: Array<ViewAttributesProvider> = emptyArray()

) :
    GestureDetector.OnGestureListener {

    private val coordinatesContainer = IntArray(2)
    private var scrollEventType: Gesture = Gesture.NONE
    private var gestureDirection = ""
    private var scrollTargetReference: WeakReference<View?> = WeakReference(null)
    private var onTouchDownXPos = 0f
    private var onTouchDownYPos = 0f
    // region GesturesListener

    override fun onShowPress(e: MotionEvent) {
        // No Op
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val decorView: View? = decorViewReference.get()
        handleTapUp(decorView, e)
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        resetScrollEventParameters()
        onTouchDownXPos = e.x
        onTouchDownYPos = e.y
        return false
    }

    fun onUp(event: MotionEvent) {
        closeScrollOrSwipeEventIfAny(decorViewReference.get(), event)
        resetScrollEventParameters()
    }

    override fun onFling(
        startDownEvent: MotionEvent,
        endUpEvent: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {

        scrollEventType = Gesture.SWIPE
        return false
    }

    override fun onScroll(
        startDownEvent: MotionEvent,
        currentMoveEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        val rumMonitor = GlobalRum.get()
        val decorView = decorViewReference.get()
        if (decorView == null || rumMonitor !is DatadogRumMonitor) {
            return false
        }

        // we only start the user action once
        if (scrollEventType == Gesture.NONE) {
            // check if we find a valid target
            val scrollTarget = findTargetForScroll(decorView, startDownEvent.x, startDownEvent.y)
            if (scrollTarget != null) {
                scrollTargetReference = WeakReference(scrollTarget)
                rumMonitor.startUserAction(Gesture.NONE.actionName)
            } else {
                return false
            }
            scrollEventType = Gesture.SCROLL
        }

        return false
    }

    override fun onLongPress(e: MotionEvent) {
        // No Op
    }

    // endregion

    // region Internal

    private fun closeScrollOrSwipeEventIfAny(decorView: View?, onUpEvent: MotionEvent) {
        if (scrollEventType == Gesture.NONE) {
            return
        }
        val registeredRumMonitor = GlobalRum.get()
        val scrollTarget = scrollTargetReference.get()
        if (decorView == null ||
            registeredRumMonitor !is DatadogRumMonitor ||
            scrollTarget == null
        ) {
            return
        }

        val targetId: String = resolveResourceNameFromId(scrollTarget.id)
        val attributes = resolveAttributes(scrollTarget, targetId, onUpEvent)
        registeredRumMonitor.stopUserAction(
            scrollEventType.actionName,
            attributes
        )
    }

    private fun resolveAttributes(
        scrollTarget: View,
        targetId: String,
        onUpEvent: MotionEvent
    ): MutableMap<String, Any?> {
        val attributes = mutableMapOf<String, Any?>(
            RumAttributes.TAG_TARGET_CLASS_NAME to scrollTarget.javaClass.canonicalName,
            RumAttributes.TAG_TARGET_RESOURCE_ID to targetId
        )
        gestureDirection = resolveGestureDirection(onUpEvent)
        attributes.put(RumAttributes.TAG_GESTURE_DIRECTION, gestureDirection)

        attributesProviders.forEach {
            it.extractAttributes(scrollTarget, attributes)
        }
        return attributes
    }

    private fun resetScrollEventParameters() {
        scrollTargetReference.clear()
        scrollEventType = Gesture.NONE
        gestureDirection = ""
        onTouchDownYPos = 0f
        onTouchDownXPos = 0f
    }

    private fun handleTapUp(decorView: View?, e: MotionEvent) {
        if (decorView != null) {
            findTargetForTap(decorView, e.x, e.y)?.let { target ->
                val targetId: String = resolveResourceNameFromId(target.id)
                val attributes = mutableMapOf<String, Any?>(
                    RumAttributes.TAG_TARGET_CLASS_NAME to target.javaClass.canonicalName,
                    RumAttributes.TAG_TARGET_RESOURCE_ID to targetId
                )
                attributesProviders.forEach {
                    it.extractAttributes(target, attributes)
                }
                GlobalRum.get().addUserAction(
                    Gesture.TAP.actionName,
                    attributes
                )
            }
        }
    }

    private fun findTargetForTap(decorView: View, x: Float, y: Float): View? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()

            if (isValidTapTarget(view)) {
                target = view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue, coordinatesContainer)
            }
        }

        devLogger.i(MSG_NO_TARGET_TAP)
        return target
    }

    private fun findTargetForScroll(decorView: View, x: Float, y: Float): View? {
        val queue = LinkedList<View>()
        queue.add(decorView)

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()

            if (isValidScrollableTarget(view)) {
                return view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue, coordinatesContainer)
            }
        }
        devLogger.i(MSG_NO_TARGET_SCROLL_SWIPE)
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

    private fun isValidTapTarget(view: View): Boolean {
        return view.isClickable && view.visibility == View.VISIBLE
    }

    private fun isValidScrollableTarget(view: View): Boolean {
        return view.visibility == View.VISIBLE && isScrollableView(view)
    }

    private fun isScrollableView(view: View): Boolean {
        return ScrollingView::class.java.isAssignableFrom(view.javaClass) ||
                AbsListView::class.java.isAssignableFrom(view.javaClass)
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

    private fun resolveGestureDirection(endEvent: MotionEvent): String {
        val diffX = endEvent.x - onTouchDownXPos
        val diffY = endEvent.y - onTouchDownYPos
        return if (abs(diffX) > abs(diffY)) {
            if (diffX > 0) {
                SCROLL_DIRECTION_LEFT
            } else {
                SCROLL_DIRECTION_RIGHT
            }
        } else {
            if (diffY > 0) {
                SCROLL_DIRECTION_DOWN
            } else {
                SCROLL_DIRECTION_UP
            }
        }
    }

    // endregion

    companion object {

        internal const val SCROLL_DIRECTION_LEFT = "left"
        internal const val SCROLL_DIRECTION_RIGHT = "right"
        internal const val SCROLL_DIRECTION_UP = "up"
        internal const val SCROLL_DIRECTION_DOWN = "down"

        internal val MSG_NO_TARGET_TAP = "We could not find a valid target for " +
            "the ${Gesture.TAP.actionName} event." +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
        internal val MSG_NO_TARGET_SCROLL_SWIPE = "We could not find a valid target for " +
            "the ${Gesture.SCROLL.actionName} or ${Gesture.SWIPE.actionName} event. " +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
    }
}
