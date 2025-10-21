/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.internal.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.graphics.withRotation
import com.datadog.android.insights.internal.domain.TimelineEvent
import com.datadog.android.insights.internal.extensions.color
import com.datadog.android.insights.internal.extensions.ms
import com.datadog.android.insights.internal.extensions.px
import com.datadog.android.rumdebugwidget.R

internal class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attrs, defaultStyle) {

    private var data: List<TimelineEvent> = emptyList()
    private var maxSize: Int = DEFAULT_MAX_SIZE

    private var logoText: String = ACTIVE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = color(R.color.widget_text)
        letterSpacing = LETTER_SPACING
        textSize = px(BASE_TEXT_SIZE_DP)
        strokeWidth = TEXT_STROKE_WIDTH
    }

    private val longTaskPaint: Paint
        get() = paint.apply { color = color(R.color.timeline_freeze_frame) }

    private val actionsFramesPaint: Paint
        get() = paint.apply { color = color(R.color.timeline_action) }

    private val resourceFramesPaint: Paint
        get() = paint.apply { color = color(R.color.timeline_resource) }

    private val slowFramesPaint: Paint
        get() = paint.apply { color = color(R.color.timeline_slow_frame) }

    private val tickPaint: Paint
        get() = paint.apply { color = color(R.color.vital_bg) }

    private val durationPaint: Paint
        get() = textPaint.apply { color = color(R.color.vital_bg) }

    private val headerPaint: Paint
        get() = textPaint.apply { color = color(R.color.widget_text) }

    private val barSize: Float
        get() = width.toFloat() / (maxSize)

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, px(CORNER_RADIUS_DP))
            }
        }
        clipToOutline = true
        logoText = ACTIVE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = MIN_WIDTH_DP + paddingLeft + paddingRight

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
                canvas.withRotation(degrees = 90f, pivotX = xOffset, pivotY = 0f) {
                    val text = item.durationNs.ms.toString()

                    @Suppress("UnsafeThirdPartyFunctionCall") // measureText() is called on a non-null string
                    val textWidth = durationPaint.measureText(text)
                    drawText(text, xOffset + height.toFloat() - textWidth, 0f, durationPaint)
                }
            }

            @Suppress("UnsafeThirdPartyFunctionCall") // measureText() is called on a non-null string
            canvas.drawText(
                logoText,
                width / 2 - headerPaint.measureText(logoText) / 2,
                height / 2f + headerPaint.textSize / 2,
                headerPaint
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
        this.textPaint.textSize = TEXT_SCALE * barSize
        invalidate()
    }

    private companion object {

        private const val DEFAULT_MAX_SIZE = 100
        private const val LETTER_SPACING = 0.2f
        private const val BASE_TEXT_SIZE_DP = 8
        private const val TEXT_STROKE_WIDTH = 0.9f
        private const val CORNER_RADIUS_DP = 12
        private const val MIN_WIDTH_DP = 100
        private const val TEXT_SCALE = 1.4f

        private const val ACTIVE = "\uD83D\uDFE2 Events"
        private const val PAUSED = "\uD83D\uDD34 Events"
    }
}
