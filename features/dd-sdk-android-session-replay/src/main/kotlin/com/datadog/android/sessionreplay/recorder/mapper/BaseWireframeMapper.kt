/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.drawable.Drawable
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.mapper.DrawableStyleExtractor
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

    internal val drawableStyleExtractor = DrawableStyleExtractor(drawableToColorMapper)

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
     * Resolves both [MobileSegment.ShapeStyle] and [MobileSegment.ShapeBorder] from a drawable,
     * extracting fill color, corner radius, and stroke/border info.
     */
    protected fun resolveBackgroundStyleInfo(
        drawable: Drawable,
        viewAlpha: Float,
        density: Float,
        internalLogger: InternalLogger
    ): Pair<MobileSegment.ShapeStyle?, MobileSegment.ShapeBorder?> {
        val styleInfo = drawableStyleExtractor.extractStyleInfo(drawable, internalLogger)

        val shapeStyle = if (styleInfo.color != null) {
            MobileSegment.ShapeStyle(
                backgroundColor = colorStringFormatter.formatColorAsHexString(styleInfo.color),
                opacity = viewAlpha,
                cornerRadius = styleInfo.cornerRadius.toLong().densityNormalized(density)
            )
        } else {
            null
        }

        val shapeBorder = if (styleInfo.borderColor != null && styleInfo.borderWidth > 0f) {
            MobileSegment.ShapeBorder(
                color = colorStringFormatter.formatColorAsHexString(styleInfo.borderColor),
                width = styleInfo.borderWidth.toLong().densityNormalized(density)
            )
        } else {
            null
        }

        @Suppress("UnsafeThirdPartyFunctionCall")
        return Pair(shapeStyle, shapeBorder)
    }
}
