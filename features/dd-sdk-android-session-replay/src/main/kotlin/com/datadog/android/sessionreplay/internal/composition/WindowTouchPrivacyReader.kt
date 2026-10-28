/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.putTouchOverrideArea
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer.Companion.INVALID_PRIVACY_LEVEL_ERROR
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Refreshes only touch tags and their screen rectangles after a redraw, on demand before recording
 * a touch. No visual mappers run here. A complete synchronous pass can establish a current policy
 * while visual capture waits for scheduling or processing; a failed or over-budget pass denies the touch.
 */
internal class WindowTouchPrivacyReader(
    private val timeProvider: CaptureTimeProvider = CaptureTimeProvider(System::nanoTime),
    private val budgetNs: Long = DEFAULT_BUDGET_NS,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND,
    private val isMainThreadWindow: (View) -> Boolean = ::isAttachedToMainLooper
) {
    private val viewUtils = ViewUtilsInternal()

    private companion object {
        val DEFAULT_BUDGET_NS = TimeUnit.MILLISECONDS.toNanos(8L)
    }

    @UiThread
    @Suppress("TooGenericExceptionCaught") // Custom View implementations must not break app touch dispatch.
    fun read(root: View): Map<Rect, TouchPrivacy>? = try {
        readWindow(root)
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { "Unable to refresh composition touch privacy" },
            e,
            onlyOnce = true
        )
        null
    }

    @UiThread
    // Guard deque access and fail closed at every budget check.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    private fun readWindow(root: View): Map<Rect, TouchPrivacy>? {
        val startedAtNs = timeProvider.elapsedRealtimeNanos()
        if (!isMainThreadWindow(root) || shouldSkipSubtree(root)) return null
        val pending = ArrayDeque<View>()
        pending.addLast(root)
        val areas = mutableMapOf<Rect, TouchPrivacy>()
        while (pending.isNotEmpty()) {
            if (timeProvider.elapsedRealtimeNanos() - startedAtNs >= budgetNs) return null
            val view = pending.removeLast()
            if (shouldSkipSubtree(view)) continue
            collectTouchOverrideArea(view, areas, internalLogger)
            if (!enqueueChildren(view, pending, startedAtNs)) return null
        }
        return areas.takeIf {
            timeProvider.elapsedRealtimeNanos() - startedAtNs < budgetNs && isMainThreadWindow(root)
        }
    }

    /** Hidden and occluded containers have touchable descendants; WebView internals remain opaque. */
    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // Adding to an unbounded deque cannot exceed its capacity.
    private fun enqueueChildren(view: View, pending: ArrayDeque<View>, startedAtNs: Long): Boolean {
        if (view is ViewGroup && view !is WebView) {
            for (index in 0 until view.childCount) {
                val child = if (timeProvider.elapsedRealtimeNanos() - startedAtNs < budgetNs) {
                    view.getChildAt(index)
                } else {
                    null
                }
                if (child == null) return false
                pending.addLast(child)
            }
        }
        return true
    }

    @UiThread
    private fun shouldSkipSubtree(view: View): Boolean = viewUtils.isNotVisible(view) ||
        viewUtils.isSystemNoise(view) || viewUtils.isOnSecondaryDisplay(view)
}

/** Shared with visual traversal so live refresh and captured overrides use identical precedence and geometry. */
@UiThread
internal fun collectTouchOverrideArea(
    view: View,
    areas: MutableMap<Rect, TouchPrivacy>,
    internalLogger: InternalLogger
) {
    val tag = view.getTag(R.id.datadog_touch_privacy) ?: return
    val location = IntArray(2)
    @Suppress("UnsafeThirdPartyFunctionCall") // The coordinate array always has two entries.
    view.getLocationOnScreen(location)
    val x = location[0]
    val y = location[1]
    val bounds = Rect(
        x - view.paddingLeft,
        y - view.paddingTop,
        x + view.width + view.paddingRight,
        y + view.height + view.paddingBottom
    )
    try {
        areas.putTouchOverrideArea(bounds, TouchPrivacy.valueOf(tag.toString().uppercase(Locale.US)))
    } catch (e: IllegalArgumentException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { INVALID_PRIVACY_LEVEL_ERROR },
            e
        )
    }
}
