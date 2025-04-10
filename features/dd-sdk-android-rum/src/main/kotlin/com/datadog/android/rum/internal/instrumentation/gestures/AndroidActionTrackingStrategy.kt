/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.View
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.view.ScrollingView
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewTarget

/**
 * Implementation of [ActionTrackingStrategy] for Android View, used to locate the target view
 * with given coordinates when tapping or scrolling on Android View.
 *
 */
internal class AndroidActionTrackingStrategy : ActionTrackingStrategy {

    private val coordinatesContainer = IntArray(2)

    override fun register(sdkCore: SdkCore, context: Context) {
        // sdkCore & context are not needed in this strategy, no-op
    }

    override fun unregister(context: Context?) {
        // sdkCore & context are not needed in this strategy, no-op
    }

    override fun findTargetForTap(view: View, x: Float, y: Float): ViewTarget? {
        return if (hitTest(view, x, y, coordinatesContainer) && isValidTapTarget(view)) {
            ViewTarget(view)
        } else {
            null
        }
    }

    override fun findTargetForScroll(view: View, x: Float, y: Float): ViewTarget? {
        return if (hitTest(view, x, y, coordinatesContainer) && isValidScrollableTarget(view)) {
            ViewTarget(view)
        } else {
            null
        }
    }

    private fun isValidTapTarget(view: View): Boolean {
        return view.isClickable && view.isVisible
    }

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE

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
