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
import com.datadog.android.internal.time.DefaultTimeProvider
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
    private val timeProvider: CaptureTimeProvider = DEFAULT_TIME_PROVIDER,
    private val budgetNs: Long = DEFAULT_BUDGET_NS,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND,
    private val isMainThreadWindow: (View) -> Boolean = { view -> isAttachedToMainLooper(view) },
    private val readCompositionTouchPrivacy: ((View, () -> Boolean) -> Map<Rect, TouchPrivacy>?)? = null
) {
    private val viewUtils = ViewUtilsInternal()

    @UiThread
    @Suppress("TooGenericExceptionCaught") // Custom View implementations must not break app touch dispatch.
    fun read(root: View): Map<Rect, TouchPrivacy>? = try {
        readWindow(root)
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { TOUCH_PRIVACY_REFRESH_FAILED_MESSAGE },
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
            val readComposition = readCompositionTouchPrivacy
            if (readComposition != null) {
                val compositionAreas = readComposition(view) {
                    timeProvider.elapsedRealtimeNanos() - startedAtNs < budgetNs
                } ?: return null
                compositionAreas.forEach { (bounds, privacy) -> areas.putTouchOverrideArea(bounds, privacy) }
            }
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

    private companion object {
        const val TOUCH_PRIVACY_REFRESH_FAILED_MESSAGE = "Unable to refresh composition touch privacy"
        val DEFAULT_BUDGET_NS = TimeUnit.MILLISECONDS.toNanos(8L)
        val DEFAULT_TIME_PROVIDER = DefaultTimeProvider().let { provider ->
            CaptureTimeProvider { provider.getDeviceElapsedTimeNanos() }
        }
    }
}

/** Shared with visual traversal so live refresh and captured overrides use identical precedence and geometry. */
@UiThread
internal fun collectTouchOverrideArea(
    view: View,
    areas: MutableMap<Rect, TouchPrivacy>,
    internalLogger: InternalLogger
) {
    val touchPrivacyTag = view.getTag(R.id.datadog_touch_privacy) ?: return
    try {
        val privacyLevel = TouchPrivacy.valueOf(touchPrivacyTag.toString().uppercase(Locale.US))
        val transformed = ViewTransformResolver.hasTransformedAncestor(view)
        val transform = if (transformed) ViewTransformResolver.resolve(view) else null
        // A bounding box may safely over-mask HIDE, but cannot authorize SHOW outside a rotated view.
        val canShow = transform?.isAxisAligned == true
        if (transformed && privacyLevel == TouchPrivacy.SHOW && !canShow) {
            return
        }
        val viewArea = if (transformed) {
            transform?.pixelBounds?.let {
                Rect(it.x.toInt(), it.y.toInt(), (it.x + it.width).toInt(), (it.y + it.height).toInt())
            } ?: Rect(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        } else {
            val locationOnScreen = IntArray(2)
            @Suppress("UnsafeThirdPartyFunctionCall") // Owned two-element coordinate array.
            view.getLocationOnScreen(locationOnScreen)
            val x = locationOnScreen[0]
            val y = locationOnScreen[1]
            Rect(
                x - view.paddingLeft,
                y - view.paddingTop,
                x + view.width + view.paddingRight,
                y + view.height + view.paddingBottom
            )
        }
        areas.putTouchOverrideArea(viewArea, privacyLevel)
    } catch (e: IllegalArgumentException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { INVALID_PRIVACY_LEVEL_ERROR },
            e
        )
    }
}
