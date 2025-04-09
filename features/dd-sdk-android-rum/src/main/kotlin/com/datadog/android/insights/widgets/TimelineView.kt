/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.datadog.android.insights.domain.TimelineEvent
import kotlin.math.roundToInt

internal class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attrs, defaultStyle) {

    private var data: Collection<TimelineEvent> = emptyList()
    private var maxSize: Int = 100

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = BLACK
        letterSpacing = 0.2f
        textSize = 8.px.toFloat()
        strokeWidth = 1f
    }

    private val longTaskPaint: Paint
        get() = paint.apply { color = RED }

    private val actionsFramesPaint: Paint
        get() = paint.apply { color = PINK }

    private val resourceFramesPaint: Paint
        get() = paint.apply { color = GREEN }

    private val slowFramesPaint: Paint
        get() = paint.apply { color = YELLOW }

    private val tickPaint: Paint
        get() = paint.apply { color = GRAYS.random() }

    private val barSize: Float
        get() = width.toFloat() / (maxSize)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = 100 + paddingLeft + paddingRight
        val minH = 100 + paddingTop + paddingBottom

        val w = resolveSize(minW, widthMeasureSpec)
        val h = 80.px

        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        var xOffset = 0f
        for (item in data) {
            val paint = when (item) {
                is TimelineEvent.Tick -> tickPaint
                is TimelineEvent.LongTask -> longTaskPaint
                is TimelineEvent.Action -> actionsFramesPaint
                is TimelineEvent.SlowFrame -> slowFramesPaint
                is TimelineEvent.Resource -> resourceFramesPaint
            }

            canvas.drawRect(xOffset, 0f, xOffset + barSize, height.toFloat(), paint)
            if (item.durationNs.ms > 0 || (item !is TimelineEvent.Tick && item !is TimelineEvent.Action)) {
                canvas.save()
                canvas.rotate(90f, xOffset, 0f)
                val text = item.durationNs.ms.toString()
                val textWidth = textPaint.measureText(text)
                canvas.drawText(text, xOffset + height.toFloat() - textWidth, 0f, textPaint)
                canvas.restore()
            }
            xOffset += barSize
        }
    }

    fun update(data: Collection<TimelineEvent>, maxSize: Int) {
        this.data = data
        this.maxSize = maxSize
        this.textPaint.textSize = 1.4f * barSize
        invalidate()
    }

    private val Int.px: Int
        get() {
            return (this * context.resources.displayMetrics.density).roundToInt()
        }

    private val Long.ms: Long
        get() = this / 1_000_000L

    companion object {
        private const val ALPHA = "FF"

        val BLACK = Color.parseColor("#${ALPHA}000000")
        val PINK = Color.parseColor("#${ALPHA}FFC0CB")
        val YELLOW = Color.parseColor("#${ALPHA}FFFF00")
        val GREEN = Color.parseColor("#${ALPHA}00FF00")
        val GRAYS = listOf(
            Color.parseColor("#${ALPHA}888888"),
//            Color.parseColor("#${ALPHA}999999"),
//            Color.parseColor("#${ALPHA}AAAAAA")

        )
        val RED = Color.parseColor("#${ALPHA}FF0000")
    }
}
