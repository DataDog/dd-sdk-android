/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * A custom [View] used to verify the PixelCapture fallback in Session Replay.
 *
 * This view has no SR mapper registered and draws purely via [Canvas], making it a reliable
 * "dark spot" test target. Without the PixelCapture mechanism, SR records a blank rectangle here.
 * With it, SR should produce a pixel-accurate [com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.ImageWireframe]
 * showing the concentric coloured rings below.
 *
 * Navigation: Home → Session Replay → Unsupported Views
 */
class PixelCaptureTestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rings = listOf(
        0xFFE53935.toInt(), // red
        0xFFFF8F00.toInt(), // amber
        0xFF43A047.toInt(), // green
        0xFF1E88E5.toInt(), // blue
        0xFF8E24AA.toInt() // purple
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(cx, cy) - paint.strokeWidth / 2f
        val ringCount = rings.size
        val ringWidth = maxRadius / ringCount

        rings.forEachIndexed { index, color ->
            val radius = maxRadius - index * ringWidth + ringWidth / 2f
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ringWidth * 0.7f
            canvas.drawCircle(cx, cy, radius, paint)
        }

        // Draw a white dot in the centre so the pattern has a clear focal point
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, ringWidth * 0.4f, paint)
    }
}
