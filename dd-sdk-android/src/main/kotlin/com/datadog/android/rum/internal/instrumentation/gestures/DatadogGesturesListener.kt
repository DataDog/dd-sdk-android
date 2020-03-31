/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.res.Resources
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference
import java.util.LinkedList

internal class DatadogGesturesListener(
    private val decorViewReference: WeakReference<View>,
    private val attributesProviders: Array<ViewAttributesProvider> = emptyArray()

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
                val targetId: String = resolveTargetIdOrResourceName(target)
                val attributes = mutableMapOf<String, Any?>(
                    RumAttributes.TAG_TARGET_CLASS_NAME to target.javaClass.canonicalName,
                    RumAttributes.TAG_TARGET_RESOURCE_ID to targetId
                )
                attributesProviders.forEach {
                    it.extractAttributes(target, attributes)
                }
                GlobalRum.get().addUserAction(
                    UI_TAP_ACTION_EVENT,
                    attributes
                )
            }
        }
    }

    private fun resolveTargetIdOrResourceName(target: View): String {
        return try {
            target.resources.getResourceEntryName(target.id) ?: targetIdAsHexa(target)
        } catch (e: Resources.NotFoundException) {
            sdkLogger.e("Could not find resource name for target:${target.id}", e)
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

        devLogger.i(MSG_NO_TARGET)
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
        internal const val UI_TAP_ACTION_EVENT = "TapEvent"

        internal const val MSG_NO_TARGET = "We could not find a valid target for the TapEvent. " +
            "The DecorView was empty and either transparent or not clickable " +
            "for this Activity"
    }
}
