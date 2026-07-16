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

/**
 * A custom [View] used by [PrivacyMatrixFragment] to exercise `TextAndInputPrivacy`'s three
 * levels. Draws either plain text, or the same text styled as an input field (an outlined box —
 * see [com.datadog.android.sessionreplay.internal.recorder.InputFieldDetector]) depending on
 * [styledAsInputField], set programmatically per instance so several instances of this same
 * class — each with a different [com.datadog.android.sessionreplay.TextAndInputPrivacy] override
 * applied via `setSessionReplayTextAndInputPrivacy` — can sit on the same screen.
 */
class PrivacyMatrixTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var styledAsInputField: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // No android:background means the *isolated* capture has no real backdrop otherwise —
        // fill explicitly so the text has real contrast to be OCR'd against.
        canvas.drawColor(Color.WHITE)

        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = height * TEXT_HEIGHT_FRACTION

        if (styledAsInputField) {
            drawInputStyledText(canvas)
        } else {
            canvas.drawText("Plain informational text", width * MARGIN_FRACTION, height * TEXT_BASELINE_FRACTION, paint)
        }
    }

    private fun drawInputStyledText(canvas: Canvas) {
        val textPadding = paint.textSize * TEXT_PADDING_FRACTION_OF_TEXT_SIZE
        val boxLeft = width * MARGIN_FRACTION
        val boxRight = width * (1f - MARGIN_FRACTION)
        val textBaseline = height * TEXT_BASELINE_FRACTION
        canvas.drawText("Cardholder name", boxLeft + textPadding, textBaseline, paint)

        val boxTop = textBaseline - paint.textSize * ASCENT_FRACTION_OF_TEXT_SIZE - textPadding
        val boxBottom = textBaseline + textPadding

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = STROKE_WIDTH_PX
        paint.color = Color.DKGRAY
        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, CORNER_RADIUS_PX, CORNER_RADIUS_PX, paint)
    }

    private companion object {
        private const val MARGIN_FRACTION = 0.08f
        private const val TEXT_HEIGHT_FRACTION = 0.3f
        private const val TEXT_BASELINE_FRACTION = 0.55f
        private const val TEXT_PADDING_FRACTION_OF_TEXT_SIZE = 0.5f
        private const val ASCENT_FRACTION_OF_TEXT_SIZE = 0.8f
        private const val STROKE_WIDTH_PX = 4f
        private const val CORNER_RADIUS_PX = 12f
    }
}
