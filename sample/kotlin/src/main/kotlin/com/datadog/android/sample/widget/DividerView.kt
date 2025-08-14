/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.datadog.android.sample.R
import kotlin.math.max

internal class DividerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs, 0, 0) {

    private val bounds = Rect()

    private val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = DEFAULT_TEXT_SIZE_SP * resources.displayMetrics.scaledDensity
        isAntiAlias = true
    }
    private val dividerPaint = android.graphics.Paint().apply {
        strokeWidth = DIVIDER_WIDTH_DP.px
        color = android.graphics.Color.LTGRAY
        isAntiAlias = true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var lineOffset: Float = DEFAULT_LINE_OFFSET_DP.px
        set(value) {
            field = value
            invalidate()
        }

    @Suppress("MemberVisibilityCanBePrivate")
    var textOffset: Float = DEFAULT_TEXT_OFFSET_DP.px
        set(value) {
            field = value
            invalidate()
        }

    var text: String = ""
        set(value) {
            field = value.uppercase()
            requestLayout()
            invalidate()
        }

    var color: Int = android.graphics.Color.GRAY
        set(value) {
            field = value
            dividerPaint.color = value
            textPaint.color = value
            invalidate()
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.DividerView, 0, 0)
            try {
                text = a.getString(R.styleable.DividerView_dv_text) ?: text
                color = a.getColor(R.styleable.DividerView_dv_color, color)
                lineOffset = a.getDimension(R.styleable.DividerView_dv_lineOffset, lineOffset)
                textOffset = a.getDimension(R.styleable.DividerView_dv_textOffset, textOffset)
                textPaint.textSize = a.getDimension(R.styleable.DividerView_dv_textSize, textPaint.textSize)
                dividerPaint.strokeWidth = a.getDimension(
                    R.styleable.DividerView_dv_strokeWidth,
                    dividerPaint.strokeWidth
                )
            } finally {
                a.recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val textHeight = bounds.height().toFloat()
        val textWidth = textPaint.measureText(text)
        val desiredWidth = (paddingLeft + paddingRight + textWidth)
            .toInt()
            .coerceAtLeast(suggestedMinimumWidth)
        val desiredHeight = (paddingTop + paddingBottom + max(textHeight, dividerPaint.strokeWidth))
            .toInt()
            .coerceAtLeast(suggestedMinimumHeight)

        val w = resolveSizeAndState(desiredWidth, widthMeasureSpec, 0)
        val h = resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)

        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val contentStart = paddingStart.toFloat()
        val contentEnd = (width - paddingEnd).toFloat()
        val textWidth = if (text.isNotEmpty()) textPaint.measureText(text) else 0f
        val textStart = (contentStart + textOffset).coerceIn(contentStart, contentEnd - textWidth)

        canvas.drawLine(contentStart, cy, textStart - lineOffset, cy, dividerPaint)

        if (text.isNotEmpty()) {
            canvas.drawText(text, textStart, height.toFloat(), textPaint)
        }

        val lineStartX = if (text.isNotEmpty()) {
            (textStart + textWidth + lineOffset).coerceAtLeast(contentStart)
        } else {
            contentStart
        }

        if (lineStartX < contentEnd) {
            canvas.drawLine(lineStartX, cy, contentEnd, cy, dividerPaint)
        }
    }

    private val Float.px: Float
        get() = this * resources.displayMetrics.density

    companion object {
        private const val DIVIDER_WIDTH_DP = 1f
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val DEFAULT_LINE_OFFSET_DP = 6f
        private const val DEFAULT_TEXT_OFFSET_DP = 24f
    }
}
