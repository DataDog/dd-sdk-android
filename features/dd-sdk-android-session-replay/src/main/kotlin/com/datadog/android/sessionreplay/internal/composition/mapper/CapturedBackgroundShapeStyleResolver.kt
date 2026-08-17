/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.composition.CapturedShapeStyle
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
    fun resolve(view: View, internalLogger: InternalLogger): CapturedShapeStyle? {
        val color = view.background?.let { drawableToColorMapper.mapDrawableToColor(it, internalLogger) }
            ?: return null
        return CapturedShapeStyle(
            backgroundColor = colorStringFormatter.formatColorAsHexString(color),
            opacity = view.alpha
        )
    }
}
