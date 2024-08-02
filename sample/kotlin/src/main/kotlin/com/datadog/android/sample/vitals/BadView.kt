/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.vitals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.min

internal class BadView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var slowFrameRate = false

    private var pixelWidth = DEFAULT_WIDTH
    private var pixelHeight = DEFAULT_HEIGHT

    val paint = Paint()
    val random = Random()

    init {
        setWillNotDraw(false)
        setWillNotCacheDrawing(true)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val requestedWidthMode = MeasureSpec.getMode(widthMeasureSpec)

        val requestedHeight = MeasureSpec.getSize(heightMeasureSpec)
        val requestedHeightMode = MeasureSpec.getMode(heightMeasureSpec)

        val desiredWidth: Int = DEFAULT_WIDTH
        val desiredHeight: Int = DEFAULT_HEIGHT

        val width = when (requestedWidthMode) {
            MeasureSpec.EXACTLY -> requestedWidth
            MeasureSpec.UNSPECIFIED -> desiredWidth
            MeasureSpec.AT_MOST -> min(requestedWidth, desiredWidth)
            else -> requestedWidth
        }

        val height = when (requestedHeightMode) {
            MeasureSpec.EXACTLY -> requestedHeight
            MeasureSpec.UNSPECIFIED -> desiredHeight
            MeasureSpec.AT_MOST -> min(requestedHeight, desiredHeight)
            else -> requestedHeight
        }

        setMeasuredDimension(width, height)
        pixelWidth = width
        pixelHeight = height
    }

    @Suppress("MagicNumber")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (slowFrameRate) {
            canvas.drawColor(Color.RED)
            repeat(200_000) {
                val i = random.nextInt(pixelWidth)
                val j = random.nextInt(pixelHeight)
                paint.color = Color.rgb(i, j, random.nextInt(256))
                canvas.drawRect(
                    i.toFloat(),
                    j.toFloat(),
                    (i + 1).toFloat(),
                    (j + 1).toFloat(),
                    paint
                )
            }
            invalidate()
        } else {
            canvas.drawColor(Color.BLUE)
        }
    }

    fun isSlow(): Boolean {
        return slowFrameRate
    }

    fun setSlow(slowFramerate: Boolean) {
        slowFrameRate = slowFramerate
        invalidate()
    }

    companion object {
        const val DEFAULT_WIDTH = 512
        const val DEFAULT_HEIGHT = 256
    }
}
