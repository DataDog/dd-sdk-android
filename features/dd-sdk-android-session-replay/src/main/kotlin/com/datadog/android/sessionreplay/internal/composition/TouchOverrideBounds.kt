/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.UiThread
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.utils.GlobalBounds
import java.util.IdentityHashMap

/** Android resolves ancestor bounds, clipChildren, clipToPadding and explicit ancestor clips. */
@UiThread
@Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Framework geometry is read only on the UI thread.
internal fun clipTouchOverrideArea(
    view: View,
    area: Rect,
    privacy: TouchPrivacy,
    shouldContinue: () -> Boolean = { true }
): Rect? {
    if (!shouldContinue()) return null
    // Preserve conservative HIDE bounds, especially the bounding box of a rotated or unsupported view.
    if (privacy == TouchPrivacy.HIDE) return area
    if (hasUncertainTouchClip(view, shouldContinue)) return null
    val visible = Rect()
    if (!view.getGlobalVisibleRect(visible)) return null
    // getGlobalVisibleRect uses root coordinates; touch records and mapper overrides use screen pixels.
    val rootLocation = IntArray(2)
    view.rootView.getLocationOnScreen(rootLocation)
    visible.offset(rootLocation[0], rootLocation[1])
    val clipped = Rect(area)
    if (!clipped.intersect(visible)) return null
    // The framework's visible-rect query applies ancestor clipBounds, but not the view's own clip.
    view.clipBounds?.let { localClip ->
        if (ViewTransformResolver.hasTransformedAncestor(view)) {
            return null
        } else {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val ownClip = Rect(localClip)
            ownClip.offset(location[0], location[1])
            if (!clipped.intersect(ownClip)) return null
        }
    }
    var ancestor: View? = view
    while (ancestor != null) {
        if (!shouldContinue()) return null
        if (ancestor.clipToOutline && !outlineContains(ancestor, clipped)) return null
        ancestor = ancestor.parent as? View
    }
    return clipped.takeIf { shouldContinue() }
}

@UiThread
@Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned identity map bounds cycles in custom hierarchies.
private fun hasUncertainTouchClip(view: View, shouldContinue: () -> Boolean): Boolean {
    val visited = IdentityHashMap<View, Boolean>()
    var current: View? = view
    while (current != null) {
        if (!shouldContinue() || visited.put(current, true) != null) return true
        if (current.animation != null) return true
        val parent = current.parent as? View
        // The visible-rect query maps child matrices, but its coordinate system excludes the root matrix.
        if (parent == null && ViewTransformResolver.hasSpatialTransform(current)) return true
        current = parent
    }
    return false
}

@UiThread
@Suppress("ReturnCount", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
private fun outlineContains(view: View, area: Rect): Boolean {
    val (rectangle, radius) = readTouchOutline(view) ?: return false
    val localBounds = GlobalBounds(
        rectangle.left.toLong(),
        rectangle.top.toLong(),
        rectangle.width().toLong(),
        rectangle.height().toLong()
    )
    val transformed = ViewTransformResolver.hasTransformedAncestor(view)
    val transform = if (transformed) ViewTransformResolver.resolve(view) ?: return false else null
    val scale = if (transformed) transform?.uniformScale ?: return false else 1f
    val bounds = if (transform != null) {
        transform.mapBounds(localBounds)
    } else {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        GlobalBounds(location[0] + localBounds.x, location[1] + localBounds.y, localBounds.width, localBounds.height)
    }
    return isInsideRoundedTouchOutline(bounds, radius * scale, area)
}

@UiThread
@Suppress("ReturnCount", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
private fun readTouchOutline(view: View): Pair<Rect, Double>? {
    val provider = view.outlineProvider ?: return null
    // These public providers have exact rectangular semantics, including on API 23-28.
    knownRectangularOutline(view, provider)?.let { return it to 0.0 }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val outline = Outline()
    val rectangle = Rect()
    try {
        provider.getOutline(view, outline)
        if (!outline.getRect(rectangle)) return null
    } catch (_: Exception) {
        return null
    }
    return rectangle to outline.radius.toDouble()
}

@UiThread
private fun knownRectangularOutline(view: View, provider: ViewOutlineProvider): Rect? = when {
    provider === ViewOutlineProvider.BOUNDS -> Rect(0, 0, view.width, view.height)
    provider === ViewOutlineProvider.PADDED_BOUNDS -> Rect(
        view.paddingLeft,
        view.paddingTop,
        view.width - view.paddingRight,
        view.height - view.paddingBottom
    )
    provider === ViewOutlineProvider.BACKGROUND -> {
        val background = view.background
        when {
            background == null -> Rect(0, 0, view.width, view.height)
            background.javaClass == ColorDrawable::class.java -> background.copyBounds()
            else -> null
        }
    }
    else -> null
}

/** A convex rounded rectangle contains an axis-aligned area only when all four corners fit. */
@Suppress("UnsafeThirdPartyFunctionCall") // maxOf only compares finite primitive coordinates.
internal fun isInsideRoundedTouchOutline(bounds: GlobalBounds, radius: Double, area: Rect): Boolean {
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height
    val validSize = bounds.width > 0 && bounds.height > 0
    val validRadius = radius.isFinite() && radius >= 0
    val validOutline = validSize && validRadius
    val fitsHorizontally = area.left >= bounds.x && area.right <= right
    val fitsVertically = area.top >= bounds.y && area.bottom <= bottom
    if (!validOutline || !fitsHorizontally || !fitsVertically) return false
    val r = radius.coerceAtMost(minOf(bounds.width, bounds.height) / 2.0)
    val dx = maxOf(bounds.x + r - area.left, area.right - (right - r), 0.0)
    val dy = maxOf(bounds.y + r - area.top, area.bottom - (bottom - r), 0.0)
    return dx * dx + dy * dy <= r * r
}
