/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.view.ScrollingView
import com.datadog.android.api.SdkCore
import com.datadog.android.internal.utils.isValidTapTarget
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewTarget
import java.lang.ref.WeakReference

/**
 * Implementation of [ActionTrackingStrategy] for Android View, used to locate the target view
 * with given coordinates when tapping or scrolling on Android View.
 *
 */
internal class AndroidActionTrackingStrategy : ActionTrackingStrategy {

    private val visibleRect = Rect()

    override fun register(sdkCore: SdkCore, context: Context) {
        // sdkCore & context are not needed in this strategy, no-op
    }

    override fun unregister(context: Context?) {
        // sdkCore & context are not needed in this strategy, no-op
    }

    override fun findTargetForTap(view: View, x: Float, y: Float): ViewTarget? {
        return if (hitTest(view, x, y, visibleRect) && view.isValidTapTarget()) {
            ViewTarget(viewRef = WeakReference(view))
        } else {
            null
        }
    }

    override fun findTargetForScroll(view: View, x: Float, y: Float): ViewTarget? {
        return if (hitTest(view, x, y, visibleRect) && isValidScrollableTarget(view)) {
            ViewTarget(viewRef = WeakReference(view))
        } else {
            null
        }
    }

    private fun hitTest(
        view: View,
        x: Float,
        y: Float,
        outRect: Rect
    ): Boolean {
        // Use getGlobalVisibleRect to get the view's bounds intersected with all ancestor
        // clip rects. This ensures views that are scrolled out of their parent's visible
        // area (e.g. a child inside a NestedScrollView/RecyclerView that extends behind a
        // BottomNavigationView) are NOT considered as hit targets in the clipped region.
        @Suppress("UnsafeThirdPartyFunctionCall") // outRect is never null
        val isVisible = view.getGlobalVisibleRect(outRect)
        if (!isVisible) return false
        return x >= outRect.left && x <= outRect.right &&
            y >= outRect.top && y <= outRect.bottom
    }

    private fun isValidScrollableTarget(view: View): Boolean {
        return view.visibility == View.VISIBLE && isScrollableView(view)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
    private fun isScrollableView(view: View): Boolean {
        return ScrollingView::class.java.isAssignableFrom(view.javaClass) ||
            AbsListView::class.java.isAssignableFrom(view.javaClass) ||
            ScrollView::class.java.isAssignableFrom(view.javaClass)
    }
}
