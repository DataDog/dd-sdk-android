/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.isVisible
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTarget
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
    private val internalLogger: InternalLogger,
    private val composeActionTrackingStrategy: ActionTrackingStrategy = NoOpActionTrackingStrategy(),
    private val androidActionTrackingStrategy: ActionTrackingStrategy = AndroidActionTrackingStrategy()
) : GestureListenerCompat() {

    private var scrollEventType: RumActionType? = null
    private var gestureDirection = ""
    private var scrollTargetReference: ViewTarget? = null
    private var onTouchDownXPos = 0f
    private var onTouchDownYPos = 0f

    // region GesturesListener

    init {
        contextRef.get()?.let {
            composeActionTrackingStrategy.register(sdkCore, it)
            androidActionTrackingStrategy.register(sdkCore, it)
        }
    }

    override fun onShowPress(e: MotionEvent) {
        // No Op
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val decorView = windowReference.get()?.decorView
        handleTapUp(decorView, e)
        return true
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
                findTarget(decorView, it.x, startDownEvent.y, isScroll = true)
            }
            scrollTarget?.let { target ->
                scrollTargetReference = target
                val attributes = resolveAttributes(target, null)
                // although we report scroll here, while it can be swipe in the end, it is fine,
                // because the final type is taken from stopAction call anyway
                rumMonitor.startAction(
                    RumActionType.SCROLL,
                    resolveViewTargetName(interactionPredicate, target),
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

    private fun findTarget(
        decorView: View,
        x: Float,
        y: Float,
        isScroll: Boolean = false
    ): ViewTarget? {
        val queue = LinkedList<View>()
        // add(index, element) instead of addFirst here is on purpose, to prevent issues with old AGP being used
        // when compiling with Android API 35.
        // Index 0 is always safe
        @Suppress("UnsafeThirdPartyFunctionCall")
        queue.add(0, decorView)
        var target: ViewTarget? = null
        var composeViewDetected = false
        while (queue.isNotEmpty()) {
            // removeAt(index) instead of removeFirst here is on purpose, to prevent issues
            // with old AGP being used when compiling with Android API 35.
            // removeAt can't fail because we checked isNotEmpty
            @Suppress("UnsafeThirdPartyFunctionCall")
            val view = queue.removeAt(0)
            composeViewDetected = composeViewDetected || isJetpackComposeView(view)
            val newTarget = if (isScroll) {
                findTargetForScroll(view, x, y)
            } else {
                findTargetForTap(view, x, y)
            }
            if (newTarget != null) {
                target = newTarget
            }
            if (view.isVisible && view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    queue.add(child)
                }
            }
        }

        if (target == null) {
            val msg = if (composeViewDetected) {
                MSG_NO_COMPOSE_TARGET
            } else {
                MSG_NO_TARGET_ACTION
            }
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { msg }
            )
        }
        return target
    }

    private fun findTargetForScroll(view: View, x: Float, y: Float): ViewTarget? {
        // return bottom-most scrollable element
        var target: ViewTarget? = null
        androidActionTrackingStrategy.findTargetForScroll(view, x, y)?.let { androidViewTarget ->
            target = androidViewTarget
        }
        composeActionTrackingStrategy.findTargetForScroll(view, x, y)?.let { composeViewTarget ->
            target = composeViewTarget
        }
        return target
    }

    private fun findTargetForTap(view: View, x: Float, y: Float): ViewTarget? {
        // return bottom-most clickable element
        var target: ViewTarget? = null
        androidActionTrackingStrategy.findTargetForTap(view, x, y)?.let { androidViewTarget ->
            target = androidViewTarget
        }
        composeActionTrackingStrategy.findTargetForTap(view, x, y)?.let { composeViewTarget ->
            target = composeViewTarget
        }
        return target
    }

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
            val downTarget = findTarget(
                decorView,
                onTouchDownXPos,
                onTouchDownYPos
            )
            val upTarget = findTarget(
                decorView,
                onUpEvent.x,
                onUpEvent.y
            )
            downTarget?.takeIf { it == upTarget }?.let { target ->
                sendTapEventWithTarget(target)
            }
        }
    }

    private fun closeScrollOrSwipeEvent(type: RumActionType, decorView: View?, onUpEvent: MotionEvent) {
        val registeredRumMonitor = GlobalRumMonitor.get(sdkCore)
        val scrollTarget = scrollTargetReference
        if (decorView == null || scrollTarget == null) {
            return
        }

        val attributes = resolveAttributes(scrollTarget, onUpEvent)
        registeredRumMonitor.stopAction(
            type,
            resolveViewTargetName(interactionPredicate, scrollTarget),
            attributes
        )
    }

    private fun resetScrollEventParameters() {
        scrollTargetReference = null
        scrollEventType = null
        gestureDirection = ""
        onTouchDownYPos = 0f
        onTouchDownXPos = 0f
    }

    private fun handleTapUp(decorView: View?, e: MotionEvent) {
        if (decorView != null) {
            findTarget(decorView, e.x, e.y)?.let { target ->
                sendTapEventWithTarget(target)
            }
        }
    }

    private fun sendTapEventWithTarget(target: ViewTarget) {
        val attributes = mutableMapOf<String, Any?>()
        target.viewRef.get()?.let { view ->
            val targetId: String = contextRef.get().resourceIdName(view.id)
            attributes[RumAttributes.ACTION_TARGET_CLASS_NAME] = view.targetClassName()
            attributes[RumAttributes.ACTION_TARGET_RESOURCE_ID] = targetId
            attributesProviders.forEach {
                it.extractAttributes(view, attributes)
            }
        }
        target.tag?.let {
            // TODO RUM-9345: Enrich Compose action target attributes.
        }
        GlobalRumMonitor.get(sdkCore).addAction(
            RumActionType.TAP,
            resolveViewTargetName(interactionPredicate, target),
            attributes
        )
    }

    private fun resolveAttributes(
        scrollTarget: ViewTarget,
        onUpEvent: MotionEvent?
    ): MutableMap<String, Any?> {
        val attributes = mutableMapOf<String, Any?>()
        scrollTarget.viewRef.get()?.let { view ->
            val targetId: String = contextRef.get().resourceIdName(view.id)
            attributes[RumAttributes.ACTION_TARGET_CLASS_NAME] = view.targetClassName()
            attributes[RumAttributes.ACTION_TARGET_RESOURCE_ID] = targetId
            attributesProviders.forEach {
                it.extractAttributes(view, attributes)
            }
        }
        scrollTarget.tag?.let {
            // TODO RUM-9345: Enrich Compose action target attributes.
        }
        if (onUpEvent != null) {
            gestureDirection = resolveGestureDirection(onUpEvent)
            attributes[RumAttributes.ACTION_GESTURE_DIRECTION] = gestureDirection
        }
        return attributes
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

        internal const val MSG_NO_COMPOSE_TARGET =
            "We could not find a valid target for the gesture " +
                "event. Compose actions tracking not enabled, or the compose view is not tagged."
        internal const val MSG_NO_TARGET_ACTION = "We could not find a valid target for " +
            "the gesture event. " +
            "The DecorView was empty and either transparent " +
            "or not clickable for this Activity."
    }
}
