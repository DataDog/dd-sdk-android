/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal object ComposeVectorPainterRasterizer {
    @Suppress("UnsafeThirdPartyFunctionCall") // Fresh owned pixels; failed draws release only this output.
    fun render(
        painter: VectorPainter,
        source: Bitmap,
        alpha: Float,
        colorFilter: ColorFilter?,
        density: Density,
        direction: LayoutDirection
    ): Bitmap {
        val pixels = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        var completed = false
        try {
            CanvasDrawScope().draw(
                density,
                direction,
                Canvas(pixels.asImageBitmap()),
                Size(source.width.toFloat(), source.height.toFloat())
            ) {
                with(painter) { draw(size, alpha, colorFilter) }
            }
            completed = true
            return pixels
        } finally {
            if (!completed) pixels.recycle()
        }
    }
}
