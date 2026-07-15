/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A basic abstract [WireframeMapper] that provides some helpful utilities.
 *
 * It provides functions to:
 *  - resolve a stable id for any [View]
 *  - converts a [Drawable] into a [MobileSegment.ShapeStyle]
 *
 *  @param T the type of the [View] to map
 *  @property viewIdentifierResolver the [ViewIdentifierResolver] (to resolve a view or children stable id)
 *  @property colorStringFormatter the [ColorStringFormatter] to transform Color into HTML hex strings
 *  @property viewBoundsResolver the [ViewBoundsResolver] to get a view boundaries in density independent units
 *  @property drawableToColorMapper the [DrawableToColorMapper] to convert a background drawable into a solid color
 */
abstract class BaseWireframeMapper<in T : View>(
    protected val viewIdentifierResolver: ViewIdentifierResolver,
    protected val colorStringFormatter: ColorStringFormatter,
    protected val viewBoundsResolver: ViewBoundsResolver,
    protected val drawableToColorMapper: DrawableToColorMapper
) : WireframeMapper<T> {

    /**
     * Resolves the [View] unique id to be used in the mapped [MobileSegment.Wireframe].
     */
    protected fun resolveViewId(view: View): Long {
        return viewIdentifierResolver.resolveViewId(view)
    }

    /**
     * Resolves the [MobileSegment.ShapeStyle] based on the [View] drawables.
     */
    protected fun resolveShapeStyle(
        drawable: Drawable,
        viewAlpha: Float,
        internalLogger: InternalLogger
    ): MobileSegment.ShapeStyle? {
        val color = drawableToColorMapper.mapDrawableToColor(drawable, internalLogger)
        return if (color != null) {
            MobileSegment.ShapeStyle(colorStringFormatter.formatColorAsHexString(color), viewAlpha)
        } else {
            null
        }
    }

    /**
     * Resolves the [MobileSegment.ShapeStyle] based on the [View] drawables, additionally
     * reading a uniform corner radius off [drawable] (see [resolveCornerRadiusPx]) and
     * density-normalizing it via [density] — the same convention every other dimension in this
     * wire format follows. Kept as a separate overload from [resolveShapeStyle] above rather than
     * adding [density] there, since that one is public/protected API on a published SDK class and
     * changing its signature would break any external subclass calling it.
     */
    protected fun resolveShapeStyle(
        drawable: Drawable,
        viewAlpha: Float,
        density: Float,
        internalLogger: InternalLogger
    ): MobileSegment.ShapeStyle? {
        val color = drawableToColorMapper.mapDrawableToColor(drawable, internalLogger)
        return if (color != null) {
            val cornerRadiusPx = resolveCornerRadiusPx(drawable)
            MobileSegment.ShapeStyle(
                backgroundColor = colorStringFormatter.formatColorAsHexString(color),
                opacity = viewAlpha,
                cornerRadius = cornerRadiusPx?.let { if (density == 0f) it else it / density }
            )
        } else {
            null
        }
    }

    /**
     * Resolves a uniform corner radius (in raw px) from [drawable], unwrapping the same wrapper
     * chain [DrawableToColorMapper] resolves for color — [RippleDrawable]/[LayerDrawable]/
     * [InsetDrawable]/[StateListDrawable] are all common ways a `<shape>` [GradientDrawable]
     * background ends up wrapped at runtime (e.g. `MaterialButton` composites its declared
     * background through some of these). Only [GradientDrawable.getCornerRadius] is read — the
     * per-corner [GradientDrawable.getCornerRadii] array isn't representable in this wire
     * format's single [MobileSegment.ShapeStyle.cornerRadius] value, so a drawable using those
     * instead falls back to square corners, same as before this function existed. Null when no
     * wrapped [GradientDrawable] is found, or its radius is unset/non-positive.
     */
    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun resolveCornerRadiusPx(drawable: Drawable): Float? {
        return when (drawable) {
            is GradientDrawable -> drawable.cornerRadius.takeIf { it > 0f }
            is RippleDrawable -> resolveLayerDrawableCornerRadiusPx(drawable)
            is LayerDrawable -> resolveLayerDrawableCornerRadiusPx(drawable)
            is InsetDrawable -> drawable.drawable?.let { resolveCornerRadiusPx(it) }
            // Drawable.getCurrent() can return null in case <selector> doesn't have an item for
            // the default case — see AndroidMDrawableToColorMapper.resolveStateListDrawable.
            is StateListDrawable -> drawable.current?.let { resolveCornerRadiusPx(it) }
            else -> null
        }
    }

    private fun resolveLayerDrawableCornerRadiusPx(drawable: LayerDrawable): Float? {
        return (0 until drawable.numberOfLayers)
            .asSequence()
            .mapNotNull { idx -> drawable.getDrawable(idx) }
            .mapNotNull { resolveCornerRadiusPx(it) }
            .firstOrNull()
    }
}
