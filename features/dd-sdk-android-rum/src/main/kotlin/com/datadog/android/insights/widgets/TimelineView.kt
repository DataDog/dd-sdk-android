/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import com.datadog.android.insights.domain.TimelineEvent
import com.datadog.android.insights.extensions.ms
import kotlin.math.roundToInt
import androidx.core.graphics.withRotation

internal class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attrs, defaultStyle) {

    private var data: List<TimelineEvent> = emptyList()
    private var maxSize: Int = 100

    private var logoText: String = ACTIVE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = BLACK
        letterSpacing = 0.2f
        textSize = 8.px.toFloat()
        strokeWidth = 0.9f
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
        get() = paint.apply { color = GRAY1 }

    private val barSize: Float
        get() = width.toFloat() / (maxSize)

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 12.px.toFloat())
            }
        }
        clipToOutline = true
        logoText = ACTIVE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = 100 + paddingLeft + paddingRight

        val w = resolveSize(minW, widthMeasureSpec)
        val h = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)

        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        var xOffset = width.toFloat() - barSize
        for (i in data.size - 1 downTo 0) {
            val item = data[i]
            val paint = when (item) {
                is TimelineEvent.Tick -> tickPaint
                is TimelineEvent.LongTask -> longTaskPaint
                is TimelineEvent.Action -> actionsFramesPaint
                is TimelineEvent.SlowFrame -> slowFramesPaint
                is TimelineEvent.Resource -> resourceFramesPaint
            }
            canvas.drawRect(xOffset, 0f, xOffset + barSize, height.toFloat(), paint)
            if (item.durationNs.ms > 0 || (item !is TimelineEvent.Tick && item !is TimelineEvent.Action)) {
                canvas.withRotation(90f, xOffset, 0f) {
                    val text = item.durationNs.ms.toString()
                    val textWidth = textPaint.measureText(text)
                    drawText(text, xOffset + height.toFloat() - textWidth, 0f, textPaint)
                }
            }

            canvas.drawText(
                logoText,
                width / 2 - textPaint.measureText(logoText) / 2,
                height / 2f + textPaint.textSize / 2,
                textPaint
            )
            xOffset -= barSize
        }
    }

    fun setPaused(isPaused: Boolean) {
        logoText = if (isPaused) PAUSED else ACTIVE
        invalidate()
    }

    fun update(data: List<TimelineEvent>, maxSize: Int) {
        this.data = data
        this.maxSize = maxSize
        this.textPaint.textSize = 1.4f * barSize
        invalidate()
    }

    private val Int.px: Int
        get() = times(context.resources.displayMetrics.density).roundToInt()

    companion object {
        val BLACK = Color.parseColor("#000000")
        val PINK = Color.parseColor("#FFC0CB")
        val YELLOW = Color.parseColor("#FFFF00")
        val GREEN = Color.parseColor("#00FF00")
        val GRAY1 = Color.parseColor("#B5B5B5")
        val RED = Color.parseColor("#FF0000")

        private const val ACTIVE = "\uD83D\uDFE2 Events"
        private const val PAUSED = "\uD83D\uDD34 Events"
    }
}
