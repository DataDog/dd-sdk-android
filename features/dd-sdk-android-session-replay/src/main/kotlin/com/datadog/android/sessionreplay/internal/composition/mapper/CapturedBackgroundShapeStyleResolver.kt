/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper

/**
 * Resolves a view's background drawable to a solid-color [CapturedShapeStyle], or null if the
 * drawable can't be reduced to a single color. Mirrors legacy `BaseWireframeMapper.resolveShapeStyle`,
 * re-inlined here rather than reused directly since that method is `protected` on a base class
 * belonging to a different wireframe model hierarchy.
 */
internal class CapturedBackgroundShapeStyleResolver(
    private val internalLogger: InternalLogger,
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
) {
    fun resolve(view: View): CapturedShapeStyle? {
        return capture(view)?.resolve()
    }

    fun capture(view: View): CapturedBackgroundColor? {
        val background = view.background
        val color = background?.let { drawableToColorMapper.mapDrawableToColor(it, internalLogger) } ?: return null

        @Suppress("DEPRECATION") // ColorDrawable opacity accounts for tint and color filters.
        val isOpaque = background is ColorDrawable && background.opacity == PixelFormat.OPAQUE &&
            (color ushr ALPHA_SHIFT) == FULLY_OPAQUE_ALPHA
        return CapturedBackgroundColor(color, colorStringFormatter, isOpaque)
    }

    private companion object {
        const val ALPHA_SHIFT = 24
        const val FULLY_OPAQUE_ALPHA = 0xff
    }
}

internal data class CapturedBackgroundColor(
    val color: Int,
    val formatter: ColorStringFormatter,
    val isOpaque: Boolean = false
) {
    fun resolve(): CapturedShapeStyle = CapturedShapeStyle(backgroundColor = formatter.formatColorAsHexString(color))
}
