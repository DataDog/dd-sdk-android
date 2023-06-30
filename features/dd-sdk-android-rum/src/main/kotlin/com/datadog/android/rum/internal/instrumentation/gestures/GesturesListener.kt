/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AbsListView
import androidx.core.view.ScrollingView
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.LinkedList
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class GesturesListener(
    private val sdkCore: SdkCore,
    private val windowReference: WeakReference<Window>,
    private val attributesProviders: Array<ViewAttributesProvider> = emptyArray(),
    private val interactionPredicate: InteractionPredicate = NoOpInteractionPredicate(),
    private val contextRef: Reference<Context>,
    private val internalLogger: InternalLogger
) : GestureDetector.OnGestureListener {

    private val coordinatesContainer = IntArray(2)
    private var scrollEventType: RumActionType? = null
    private var gestureDirection = ""
    private var scrollTargetReference: WeakReference<View?> = WeakReference(null)
    private var onTouchDownXPos = 0f
    private var onTouchDownYPos = 0f

    // region GesturesListener

    override fun onShowPress(e: MotionEvent) {
        // No Op
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val decorView = windowReference.get()?.decorView
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
        val decorView = windowReference.get()?.decorView
        closeScrollOrSwipeEventIfAny(decorView, event)
        resetScrollEventParameters()
    }

    override fun onFling(
        startDownEvent: MotionEvent,
        endUpEvent: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        scrollEventType = RumActionType.SWIPE
        return false
    }

    @Suppress("ReturnCount")
    override fun onScroll(
        startDownEvent: MotionEvent,
        currentMoveEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        val decorView = windowReference.get()?.decorView ?: return false

        // we only start the user action once
        if (scrollEventType == null) {
            // check if we find a valid target
            val scrollTarget = findTargetForScroll(decorView, startDownEvent.x, startDownEvent.y)
            if (scrollTarget != null) {
                scrollTargetReference = WeakReference(scrollTarget)
                val targetId: String = contextRef.get().resourceIdName(scrollTarget.id)
                val attributes = resolveAttributes(scrollTarget, targetId, null)
                // although we report scroll here, while it can be swipe in the end, it is fine,
                // because the final type is taken from stopAction call anyway
                rumMonitor.startAction(
                    RumActionType.SCROLL,
                    resolveTargetName(interactionPredicate, scrollTarget),
                    attributes
                )
            } else {
                return false
            }
            scrollEventType = RumActionType.SCROLL
        }

        return false
    }

    override fun onLongPress(e: MotionEvent) {
        // No Op
    }

    // endregion

    // region Internal

    private fun closeScrollOrSwipeEventIfAny(decorView: View?, onUpEvent: MotionEvent) {
        val type = scrollEventType ?: return

        val registeredRumMonitor = GlobalRumMonitor.get(sdkCore)
        val scrollTarget = scrollTargetReference.get()
        if (decorView == null ||
            scrollTarget == null
        ) {
            return
        }

        val targetId: String = contextRef.get().resourceIdName(scrollTarget.id)
        val attributes = resolveAttributes(scrollTarget, targetId, onUpEvent)
        registeredRumMonitor.stopAction(
            type,
            resolveTargetName(interactionPredicate, scrollTarget),
            attributes
        )
    }

    private fun resolveAttributes(
        scrollTarget: View,
        targetId: String,
        onUpEvent: MotionEvent?
    ): MutableMap<String, Any?> {
        val attributes = mutableMapOf<String, Any?>(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollTarget.targetClassName(),
            RumAttributes.ACTION_TARGET_RESOURCE_ID to targetId
        )
        if (onUpEvent != null) {
            gestureDirection = resolveGestureDirection(onUpEvent)
            attributes.put(RumAttributes.ACTION_GESTURE_DIRECTION, gestureDirection)
        }

        attributesProviders.forEach {
            it.extractAttributes(scrollTarget, attributes)
        }
        return attributes
    }

    private fun resetScrollEventParameters() {
        scrollTargetReference.clear()
        scrollEventType = null
        gestureDirection = ""
        onTouchDownYPos = 0f
        onTouchDownXPos = 0f
    }

    private fun handleTapUp(decorView: View?, e: MotionEvent) {
        if (decorView != null) {
            findTargetForTap(decorView, e.x, e.y)?.let { target ->
                val targetId: String = contextRef.get().resourceIdName(target.id)
                val attributes = mutableMapOf<String, Any?>(
                    RumAttributes.ACTION_TARGET_CLASS_NAME to target.targetClassName(),
                    RumAttributes.ACTION_TARGET_RESOURCE_ID to targetId
                )
                attributesProviders.forEach {
                    it.extractAttributes(target, attributes)
                }
                GlobalRumMonitor.get(sdkCore).addAction(
                    RumActionType.TAP,
                    resolveTargetName(interactionPredicate, target),
                    attributes
                )
            }
        }
    }

    private fun findTargetForTap(decorView: View, x: Float, y: Float): View? {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null
        var notifyMissingTarget = true

        while (queue.isNotEmpty()) {
            // removeFirst can't fail because we checked isNotEmpty
            @Suppress("UnsafeThirdPartyFunctionCall")
            val view = queue.removeFirst()
            if (queue.isEmpty() && isJetpackComposeView(view)) {
                notifyMissingTarget = false
            }

            if (isValidTapTarget(view)) {
                target = view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue, coordinatesContainer)
            }
        }

        if (target == null && notifyMissingTarget) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { MSG_NO_TARGET_TAP }
            )
        }
        return target
    }

    private fun findTargetForScroll(decorView: View, x: Float, y: Float): View? {
        val queue = LinkedList<View>()
        queue.add(decorView)

        var notifyMissingTarget = true
        while (queue.isNotEmpty()) {
            // removeFirst can't fail because we checked isNotEmpty
            @Suppress("UnsafeThirdPartyFunctionCall")
            val view = queue.removeFirst()
            if (queue.isEmpty() && isJetpackComposeView(view)) {
                notifyMissingTarget = false
            }

            if (isValidScrollableTarget(view)) {
                return view
            }

            if (view is ViewGroup) {
                handleViewGroup(view, x, y, queue, coordinatesContainer)
            }
        }

        if (notifyMissingTarget) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { MSG_NO_TARGET_SCROLL_SWIPE }
            )
        }

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
        @Suppress("UnsafeThirdPartyFunctionCall") // container always have the correct size
        view.getLocationInWindow(container)
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
                SCROLL_DIRECTION_RIGHT
            } else {
                SCROLL_DIRECTION_LEFT
            }
        } else {
            if (diffY > 0) {
                SCROLL_DIRECTION_DOWN
            } else {
                SCROLL_DIRECTION_UP
            }
        }
    }

    private fun isJetpackComposeView(view: View): Boolean {
        // startsWith here is to make testing easier: mocks don't have name exactly
        // like this, and writing manual stub is not possible, because some necessary
        // methods are final.
        return view::class.java.name.startsWith("androidx.compose.ui.platform.ComposeView")
    }

    // endregion

    companion object {

        internal const val SCROLL_DIRECTION_LEFT = "left"
        internal const val SCROLL_DIRECTION_RIGHT = "right"
        internal const val SCROLL_DIRECTION_UP = "up"
        internal const val SCROLL_DIRECTION_DOWN = "down"

        internal val MSG_NO_TARGET_TAP = "We could not find a valid target for " +
            "the ${RumActionType.TAP.name} event." +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
        internal val MSG_NO_TARGET_SCROLL_SWIPE = "We could not find a valid target for " +
            "the ${RumActionType.SCROLL.name} or ${RumActionType.SWIPE.name} event. " +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
    }
}
