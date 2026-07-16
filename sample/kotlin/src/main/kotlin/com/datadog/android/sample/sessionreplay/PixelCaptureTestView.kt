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
 * A custom [View] used to verify the PixelCapture fallback in Session Replay, including the
 * experimental ML Kit text-detection pass over it (see
 * [com.datadog.android.sessionreplay.internal.recorder.DefaultTextDetector]).
 *
 * This view has no SR mapper registered and draws purely via [Canvas], making it a reliable
 * "dark spot" test target. Without the PixelCapture mechanism, SR records a blank rectangle here.
 * With it, SR should produce a pixel-accurate [com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.ImageWireframe]
 * showing the content below.
 *
 * Below the rings (the original PixelCapture test target), four rows exercise the text-detection
 * pipeline's three buckets: plain text with no field styling, text underlined the way a "filled"
 * Material text field draws it, text boxed the way an "outlined" one does — see
 * [com.datadog.android.sessionreplay.internal.recorder.InputFieldDetector] for the heuristic that
 * tells the latter two apart from the first — and a bare, self-blinking cursor with **no text at
 * all**, the one case only
 * [com.datadog.android.sessionreplay.internal.recorder.BlinkingCursorTracker]'s cadence-based
 * signal can catch (there's nothing here for OCR or a pixel-border scan to even anchor to).
 *
 * Navigation: Home → Session Replay → Unsupported Views
 */
class PixelCaptureTestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Toggled once per onDraw so the cursor visibly blinks — not read anywhere else.
    private var cursorVisible = true

    private val rings = listOf(
        0xFFE53935.toInt(), // red
        0xFFFF8F00.toInt(), // amber
        0xFF43A047.toInt(), // green
        0xFF1E88E5.toInt(), // blue
        0xFF8E24AA.toInt() // purple
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // This view has no android:background, so PixelCapture's *isolated* capture of it (see
        // that class's doc — it draws only what this view itself renders, never a parent's
        // background) has no real backdrop behind it, unlike the live on-screen composite where
        // the parent's white background shows through. An explicit fill here keeps the isolated
        // bitmap's contrast consistent with what's visible on screen — otherwise none of the text
        // below has real contrast to be OCR'd against in the captured bitmap itself.
        canvas.drawColor(Color.WHITE)

        val ringsBandHeight = height * RINGS_BAND_FRACTION
        drawRings(canvas, width / 2f, ringsBandHeight / 2f, ringsBandHeight)

        val rowHeight = (height - ringsBandHeight) / TEXT_ROW_COUNT
        var rowTop = ringsBandHeight
        drawPlainText(canvas, rowTop, rowHeight)
        rowTop += rowHeight
        drawUnderlinedField(canvas, rowTop, rowHeight)
        rowTop += rowHeight
        drawOutlinedField(canvas, rowTop, rowHeight)
        rowTop += rowHeight
        drawBlinkingCursor(canvas, rowTop, rowHeight)
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, bandHeight: Float) {
        val maxRadius = min(cx, bandHeight / 2f) - paint.strokeWidth / 2f
        val ringWidth = maxRadius / rings.size

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

    /** Text with no field styling at all — the "has text, not an input field" control case. */
    private fun drawPlainText(canvas: Canvas, rowTop: Float, rowHeight: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = rowHeight * TEXT_HEIGHT_FRACTION
        val baseline = rowTop + rowHeight * TEXT_BASELINE_FRACTION
        canvas.drawText("Plain text, no field", width * TEXT_MARGIN_FRACTION, baseline, paint)
    }

    /**
     * Mimics a "filled"-style Material text field: a value with a stroke directly beneath it.
     * The gap between baseline and stroke is proportional to the text's own size — matching a
     * real field's padding — not to [rowHeight], which only exists to lay out this demo screen's
     * rows and has no equivalent in a real field.
     */
    private fun drawUnderlinedField(canvas: Canvas, rowTop: Float, rowHeight: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = rowHeight * TEXT_HEIGHT_FRACTION
        val left = width * TEXT_MARGIN_FRACTION
        val right = width * (1f - TEXT_MARGIN_FRACTION)
        val baseline = rowTop + rowHeight * TEXT_BASELINE_FRACTION
        canvas.drawText("Underlined value", left, baseline, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = FIELD_STROKE_WIDTH_PX
        paint.color = Color.DKGRAY
        val underlineY = baseline + paint.textSize * UNDERLINE_GAP_FRACTION_OF_TEXT_SIZE
        canvas.drawLine(left, underlineY, right, underlineY, paint)
    }

    /**
     * Mimics an "outlined"-style Material text field: a value inside a rounded-rect border, full
     * row width (as a real field usually is) but with vertical padding proportional to the text's
     * own size — not [rowHeight] — matching how much padding a real field actually draws around
     * its text, regardless of how tall this demo's row happens to be.
     */
    private fun drawOutlinedField(canvas: Canvas, rowTop: Float, rowHeight: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = rowHeight * TEXT_HEIGHT_FRACTION
        val textPadding = paint.textSize * OUTLINE_TEXT_PADDING_FRACTION_OF_TEXT_SIZE
        val boxLeft = width * TEXT_MARGIN_FRACTION
        val boxRight = width * (1f - TEXT_MARGIN_FRACTION)
        val textBaseline = rowTop + rowHeight * TEXT_BASELINE_FRACTION
        canvas.drawText("Outlined value", boxLeft + textPadding, textBaseline, paint)

        val boxTop = textBaseline - paint.textSize * OUTLINE_ASCENT_FRACTION_OF_TEXT_SIZE - textPadding
        val boxBottom = textBaseline + textPadding

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = FIELD_STROKE_WIDTH_PX
        paint.color = Color.DKGRAY
        canvas.drawRoundRect(
            boxLeft,
            boxTop,
            boxRight,
            boxBottom,
            OUTLINE_CORNER_RADIUS_PX,
            OUTLINE_CORNER_RADIUS_PX,
            paint
        )
    }

    /**
     * A bare vertical caret, toggled on/off and redrawn roughly every [BLINK_INTERVAL_MS] —
     * Android's own default text-cursor blink cadence. No text at all is drawn here, so this row
     * exercises [com.datadog.android.sessionreplay.internal.recorder.BlinkingCursorTracker]'s
     * signal in isolation: nothing else in this pipeline (OCR, the border/underline heuristic)
     * has anything to work with here.
     */
    private fun drawBlinkingCursor(canvas: Canvas, rowTop: Float, rowHeight: Float) {
        if (cursorVisible) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = FIELD_STROKE_WIDTH_PX
            paint.color = Color.BLACK
            val cursorX = width * TEXT_MARGIN_FRACTION
            canvas.drawLine(
                cursorX,
                rowTop + rowHeight * CURSOR_INSET_FRACTION,
                cursorX,
                rowTop + rowHeight * (1f - CURSOR_INSET_FRACTION),
                paint
            )
        }
        cursorVisible = !cursorVisible
        postInvalidateDelayed(BLINK_INTERVAL_MS)
    }

    private companion object {
        private const val RINGS_BAND_FRACTION = 0.45f
        private const val TEXT_ROW_COUNT = 4f
        private const val TEXT_MARGIN_FRACTION = 0.08f
        private const val TEXT_HEIGHT_FRACTION = 0.3f
        private const val TEXT_BASELINE_FRACTION = 0.55f
        private const val UNDERLINE_GAP_FRACTION_OF_TEXT_SIZE = 0.25f
        private const val OUTLINE_ASCENT_FRACTION_OF_TEXT_SIZE = 0.8f
        private const val OUTLINE_TEXT_PADDING_FRACTION_OF_TEXT_SIZE = 0.5f
        private const val OUTLINE_CORNER_RADIUS_PX = 12f
        private const val FIELD_STROKE_WIDTH_PX = 4f
        private const val CURSOR_INSET_FRACTION = 0.2f
        private const val BLINK_INTERVAL_MS = 500L
    }
}
