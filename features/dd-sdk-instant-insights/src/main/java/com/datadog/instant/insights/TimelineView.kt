/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.datadog.instant.insights.timeline.TimelineEvent
import kotlin.math.max
import kotlin.math.roundToInt

internal class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attrs, defaultStyle) {

    private var data: Collection<TimelineEvent> = emptyList()
    private var tickDurationNS = 1L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val slowFramesPaint: Paint
        get() = paint.apply { color = YELLOW }

    private val longTaskPaint: Paint
        get() = paint.apply { color = RED }

    private val tickPaint: Paint
        get() = paint.apply { color = GRAYS.random() }

    private val actionsFramesPaint: Paint
        get() = paint.apply { color = PINK }

    private val resourceFramesPaint: Paint
        get() = paint.apply { color = GREEN }

    private val barSize: Float
        get() = width.toFloat() / (100)

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
                is TimelineEvent.Action -> actionsFramesPaint
                is TimelineEvent.Resource -> resourceFramesPaint
                is TimelineEvent.SlowFrame -> slowFramesPaint
                is TimelineEvent.Tick -> tickPaint
                is TimelineEvent.LongTask -> longTaskPaint
            }
            val barSize = barSize * max(1, item.durationNs / (tickDurationNS))
            canvas.drawRect(xOffset, 0f, xOffset + barSize, height.toFloat(), paint)
            xOffset += barSize
        }
    }

    fun update(data: Collection<TimelineEvent>, tickDuration: Long) {
        this.data = data
        this.tickDurationNS = tickDuration * 1000_000
        invalidate()
    }

    private val Int.px: Int
        get() {
            return (this * context.resources.displayMetrics.density).roundToInt()
        }

    companion object {
        private const val ALPHA = "FF"
        val PINK = Color.parseColor("#${ALPHA}FFC0CB")
        val YELLOW = Color.parseColor("#${ALPHA}FFFF00")
        val GREEN = Color.parseColor("#${ALPHA}00FF00")
        val GRAYS = listOf(
            Color.parseColor("#${ALPHA}888888"),
            Color.parseColor("#${ALPHA}999999"),
            Color.parseColor("#${ALPHA}AAAAAA")

        )
        val RED = Color.parseColor("#${ALPHA}FF0000")
    }
}
