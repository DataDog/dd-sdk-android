/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

@Suppress("UndocumentedPublicClass")
abstract class BaseWireframeMapper<T : View, S : MobileSegment.Wireframe>(
    protected val viewIdentifierResolver: ViewIdentifierResolver,
    protected val colorStringFormatter: ColorStringFormatter,
    protected val viewBoundsResolver: ViewBoundsResolver,
    protected val drawableToColorMapper: DrawableToColorMapper
) : WireframeMapper<T, S> {

    /**
     * Resolves the [View] unique id to be used in the mapped [MobileSegment.Wireframe].
     */
    protected fun resolveViewId(view: View): Long {
        return viewIdentifierResolver.resolveViewId(view)
    }

    /**
     * Resolves the [MobileSegment.ShapeStyle] based on the [View] drawables.
     */
    protected fun resolveShapeStyle(drawable: Drawable, viewAlpha: Float): MobileSegment.ShapeStyle? {
        val color = drawableToColorMapper.mapDrawableToColor(drawable)
        return if (color != null) {
            MobileSegment.ShapeStyle(colorStringFormatter.formatColorAsHexString(color), viewAlpha)
        } else {
            null
        }
    }

    companion object {
        internal const val OPAQUE_ALPHA_VALUE: Int = 255
    }
}
