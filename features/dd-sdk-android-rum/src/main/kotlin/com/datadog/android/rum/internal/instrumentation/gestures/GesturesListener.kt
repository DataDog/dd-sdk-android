/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class GesturesListener(
    private val sdkCore: SdkCore,
    private val windowReference: WeakReference<Window>,
    private val attributesProviders: Array<ViewAttributesProvider> = emptyArray(),
    private val interactionPredicate: InteractionPredicate = NoOpInteractionPredicate(),
    private val contextRef: Reference<Context>,
    private val internalLogger: InternalLogger
) : GestureListenerCompat() {

    private var scrollEventType: RumActionType? = null
    private var gestureDirection = ""
    private var scrollTargetReference: WeakReference<View?> = WeakReference(null)
    private var onTouchDownXPos = 0f
    private var onTouchDownYPos = 0f
    private val androidActionTrackingStrategy: ActionTrackingStrategy =
        AndroidActionTrackingStrategy(internalLogger)

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
        startDownEvent: MotionEvent?,
        endUpEvent: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        scrollEventType = RumActionType.SWIPE
        return false
    }

    @Suppress("ReturnCount")
    override fun onScroll(
        startDownEvent: MotionEvent?,
        currentMoveEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        val decorView = windowReference.get()?.decorView ?: return false

        // we only start the user action once
        if (scrollEventType == null) {
            // check if we find a valid target
            val scrollTarget = startDownEvent?.let {
                androidActionTrackingStrategy.findTargetForScroll(decorView, it.x, startDownEvent.y)
            }
            scrollTarget?.view?.let { target ->
                scrollTargetReference = WeakReference(target)
                val targetId: String = contextRef.get().resourceIdName(target.id)
                val attributes = resolveAttributes(target, targetId, null)
                // although we report scroll here, while it can be swipe in the end, it is fine,
                // because the final type is taken from stopAction call anyway
                rumMonitor.startAction(
                    RumActionType.SCROLL,
                    resolveTargetName(interactionPredicate, target),
                    attributes
                )
                scrollEventType = RumActionType.SCROLL
            } ?: return false
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {
        // No Op
    }

    // endregion

    // region Internal

    private fun closeScrollOrSwipeEventIfAny(decorView: View?, onUpEvent: MotionEvent) {
        val type = scrollEventType
        if (type == null) {
            closeScrollAsTap(decorView, onUpEvent)
        } else {
            closeScrollOrSwipeEvent(type, decorView, onUpEvent)
        }
    }

    private fun closeScrollAsTap(decorView: View?, onUpEvent: MotionEvent) {
        if (decorView != null) {
            val downTarget = androidActionTrackingStrategy.findTargetForTap(
                decorView,
                onTouchDownXPos,
                onTouchDownYPos
            )
            val upTarget = androidActionTrackingStrategy.findTargetForTap(
                decorView,
                onUpEvent.x,
                onUpEvent.y
            )
            downTarget?.view?.takeIf { it == upTarget?.view }?.let { target ->
                sendTapEventWithTarget(target)
            }
        }
    }

    private fun closeScrollOrSwipeEvent(type: RumActionType, decorView: View?, onUpEvent: MotionEvent) {
        val registeredRumMonitor = GlobalRumMonitor.get(sdkCore)
        val scrollTarget = scrollTargetReference.get()
        if (decorView == null || scrollTarget == null) {
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
            androidActionTrackingStrategy.findTargetForTap(
                decorView,
                e.x,
                e.y
            )?.view?.let { target ->
                sendTapEventWithTarget(target)
            }
        }
    }

    private fun sendTapEventWithTarget(target: View) {
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

    // endregion

    companion object {

        internal const val SCROLL_DIRECTION_LEFT = "left"
        internal const val SCROLL_DIRECTION_RIGHT = "right"
        internal const val SCROLL_DIRECTION_UP = "up"
        internal const val SCROLL_DIRECTION_DOWN = "down"

        internal val MSG_NO_TARGET_TAP = "We could not find a valid target for " +
            "the ${RumActionType.TAP.name} event. " +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
        internal val MSG_NO_TARGET_SCROLL_SWIPE = "We could not find a valid target for " +
            "the ${RumActionType.SCROLL.name} or ${RumActionType.SWIPE.name} event. " +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
    }
}
