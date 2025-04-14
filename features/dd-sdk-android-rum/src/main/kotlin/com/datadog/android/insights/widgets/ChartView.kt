/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.datadog.android.insights.extensions.color
import com.datadog.android.insights.extensions.px
import com.datadog.android.insights.extensions.round
import com.datadog.android.internal.collections.EvictingQueue
import com.datadog.android.rum.R
import java.util.Collections.max
import java.util.Collections.min

internal class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attrs, defaultStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = color(R.color.vital_chart)
        strokeWidth = 4f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = color(R.color.widget_text)
        textSize = px(11)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = color(R.color.widget_text)
        textSize = px(12)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        clipToOutline = true
    }

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var chartEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var value: String = ""

    private var data = EvictingQueue<Double>(400)
    private var dataAveraged = listOf<Double>()
    private val textMargin = px(6)
    private var yMin = Double.MAX_VALUE
    private var yMax = Double.MIN_VALUE
    private val yRange: Double
        get() = yMax - yMin

    fun update(newData: Double) {
        if (newData.isNaN()) return
        data.add(newData)

        val windowSize = 4
        if (data.size > windowSize) {
            dataAveraged = data.toList().windowed(size = windowSize, step = 1) { window ->
                window.average()
            }

            yMin = min(dataAveraged)
            yMax = max(dataAveraged)
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)

        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = dataAveraged.toList()
        if (chartEnabled) drawChart(data, canvas)
        drawText(data, canvas)
    }

    private fun drawText(
        data: List<Double>,
        canvas: Canvas
    ) {
        val value = data.lastOrNull().round(2).toString()
        val labelWidth = labelPaint.measureText(label)
        val valueWidth = valuePaint.measureText(value)

        canvas.drawText(
            label,
            width / 2 - labelWidth / 2,
            height / 2 - textMargin / 2,
            labelPaint
        )

        canvas.drawText(
            value,
            width / 2 - valueWidth / 2,
            height / 2 + textMargin / 2 + valuePaint.textSize,
            valuePaint
        )
    }

    private fun drawChart(
        data: List<Double>,
        canvas: Canvas
    ) {
        if (data.size >= 2) {
            val xStep = width.toDouble() / data.size

            for (i in 1 until data.size) {
                val x0 = xStep * (i - 1)
                val y0 = height - height * (data[i - 1] - yMin) / yRange
                val x1 = xStep * i
                val y1 = height - height * (data[i] - yMin) / yRange
                canvas.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), paint)
            }
        }
    }
}
