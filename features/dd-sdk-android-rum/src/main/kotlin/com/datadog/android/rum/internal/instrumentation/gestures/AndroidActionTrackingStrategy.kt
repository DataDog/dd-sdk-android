/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.view.ScrollingView
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesListener.Companion.MSG_NO_TARGET_SCROLL_SWIPE
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesListener.Companion.MSG_NO_TARGET_TAP
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewTarget
import java.util.LinkedList

/**
 * Implementation of [ActionTrackingStrategy] for Android View, used to locate the target view
 * with given coordinates when tapping or scrolling on Android View.
 *
 * @param internalLogger used to log information when finding the view.
 */
internal class AndroidActionTrackingStrategy(
    private val internalLogger: InternalLogger
) : ActionTrackingStrategy {

    private val coordinatesContainer = IntArray(2)

    override fun findTargetForTap(decorView: View, x: Float, y: Float): ViewTarget {
        val queue = LinkedList<View>()
        queue.addFirst(decorView)
        var target: View? = null
        var notifyMissingTarget = true

        while (queue.isNotEmpty()) {
            // removeFirst can't fail because we checked isNotEmpty
            @Suppress("UnsafeThirdPartyFunctionCall")
            val view = queue.removeFirst()
            // TODO RUM-9289: Move Jetpack Compose Logic to the Compose implementation.
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
        return ViewTarget(target)
    }

    override fun findTargetForScroll(decorView: View, x: Float, y: Float): ViewTarget? {
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
                return ViewTarget(view)
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

    private fun isJetpackComposeView(view: View): Boolean {
        // startsWith here is to make testing easier: mocks don't have name exactly
        // like this, and writing manual stub is not possible, because some necessary
        // methods are final.
        return view::class.java.name.startsWith("androidx.compose.ui.platform.ComposeView")
    }

    private fun isValidTapTarget(view: View): Boolean {
        return view.isClickable && view.isVisible
    }

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE

    private fun handleViewGroup(
        view: ViewGroup,
        x: Float,
        y: Float,
        stack: LinkedList<View>,
        coordinatesContainer: IntArray
    ) {
        if (!view.isVisible) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (hitTest(child, x, y, coordinatesContainer)) {
                stack.add(child)
            }
        }
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

    private fun isValidScrollableTarget(view: View): Boolean {
        return view.isVisible && isScrollableView(view)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
    private fun isScrollableView(view: View): Boolean {
        return ScrollingView::class.java.isAssignableFrom(view.javaClass) ||
            AbsListView::class.java.isAssignableFrom(view.javaClass) ||
            ScrollView::class.java.isAssignableFrom(view.javaClass)
    }
}
