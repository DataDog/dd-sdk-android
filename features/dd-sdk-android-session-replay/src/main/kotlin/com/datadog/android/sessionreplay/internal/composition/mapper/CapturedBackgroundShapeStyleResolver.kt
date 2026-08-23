/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

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
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
) {
    @Suppress("ReturnCount")
    fun resolve(view: View, internalLogger: InternalLogger): CapturedShapeStyle? {
        val color = view.background?.let { drawableToColorMapper.mapDrawableToColor(it, internalLogger) }
            ?: return null
        // A fully transparent background (e.g. an explicit ColorDrawable(Color.TRANSPARENT), common
        // on fragment-transaction containers) carries no visual information at all - emitting a
        // wireframe for it anyway relies entirely on every renderer correctly treating an alpha-0
        // color as invisible. Treating it the same as "no background" removes that dependency
        // outright instead of gambling on it. Extracted via plain bit arithmetic rather than
        // android.graphics.Color.alpha() - the latter isn't functional under plain JVM unit tests.
        val alpha = (color ushr ALPHA_SHIFT_BITS) and ALPHA_MASK
        if (alpha == 0) return null
        return CapturedShapeStyle(
            backgroundColor = colorStringFormatter.formatColorAsHexString(color),
            opacity = view.alpha
        )
    }

    private companion object {
        const val ALPHA_SHIFT_BITS = 24
        const val ALPHA_MASK = 0xFF
    }
}
