/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.annotation.WorkerThread
import kotlin.math.abs

/**
 * Experimental, purely pixel-based: guesses whether a detected text region ([textBounds], from
 * [DefaultTextDetector]'s ML Kit pass) sits inside an input field, as opposed to being static
 * text. ML Kit's text recognizer has no notion of widget type — this is a heuristic layered on
 * top of it, not something OCR itself reports.
 *
 * Looks for the two visual signatures a Material text field actually draws around its text —
 * neither requires the field to be focused, unlike a blinking-cursor-based signal:
 * - [hasUnderline]: a solid horizontal stroke directly beneath the text (the "filled"/legacy
 *   text field style).
 * - [hasOutline]: a rectangular stroke around the text with some padding (the "outlined" style).
 *
 * Both compare against [backgroundColor] rather than a fixed color, so this works regardless of
 * the host app's theme — only relative contrast against this specific bitmap's own background
 * matters, not any particular color value.
 *
 * Deliberately reads only the thin edge bands actually needed for each check via bulk
 * [Bitmap.getPixels] calls — never the whole bitmap — since this runs once per freshly-captured
 * region alongside OCR, and [PixelCapture]'s own per-second capture budget (see
 * [PixelCapture.PIXEL_CAPTURE_BUDGET_PER_SEC_MS]) already treats UI-thread time as scarce; this
 * component tracks and periodically logs its own overhead (see [recordStats]) specifically so
 * that budget tradeoff can be judged from real numbers instead of guessing.
 */
internal class InputFieldDetector {

    private var checkCount = 0
    private var totalDurationNs = 0L
    private var maxDurationNs = 0L
    private var inputFieldCount = 0

    /**
     * True if any of [textBounds] appears to sit inside an input field. Always runs every check
     * for every region (rather than stopping at the first match) so [recordStats] reflects the
     * true per-capture cost, not a best case that a real screen with several text regions
     * wouldn't get.
     */
    @WorkerThread
    fun looksLikeInputField(bitmap: Bitmap, textBounds: List<Rect>): Boolean {
        val startNs = System.nanoTime()
        val backgroundColor = bitmap.getPixel(0, 0)
        var found = false
        textBounds.forEach { rect ->
            if (hasUnderline(bitmap, rect, backgroundColor) || hasOutline(bitmap, rect, backgroundColor)) {
                found = true
            }
        }
        recordStats(System.nanoTime() - startNs, found)
        return found
    }

    /**
     * Looks for a solid horizontal run below [textRect] — the underline a "filled"-style Material
     * text field draws under its text. Scans a whole band of candidate rows, scaled to the text's
     * own height, rather than one fixed-size offset — real fields vary in how much gap they leave
     * between baseline and stroke, and this has no ground truth to calibrate a single value
     * against.
     */
    private fun hasUnderline(bitmap: Bitmap, textRect: Rect, backgroundColor: Int): Boolean {
        val left = (textRect.left - textRect.width() * EDGE_MARGIN_FRACTION).toInt().coerceAtLeast(0)
        val right = (textRect.right + textRect.width() * EDGE_MARGIN_FRACTION).toInt().coerceAtMost(bitmap.width)
        val bandTop = textRect.bottom.coerceAtMost(bitmap.height)
        val scanDepth = (textRect.height() * UNDERLINE_SCAN_FRACTION).toInt()
            .coerceIn(MIN_UNDERLINE_SCAN_ROWS, MAX_UNDERLINE_SCAN_ROWS)
        val bandBottom = (bandTop + scanDepth).coerceAtMost(bitmap.height)
        return hasHorizontalStroke(bitmap, left, right - left, bandTop, bandBottom, backgroundColor)
    }

    /**
     * Looks for a rectangular stroke around [textRect] — the box an "outlined"-style Material
     * text field draws around its text plus its internal padding. Each of the 4 sides is checked
     * as a *band* scaled to the text's own dimensions (not one exact offset), for the same reason
     * as [hasUnderline]: real padding varies, and there's no ground truth to hard-code one value
     * against. Requires [MIN_EDGES_FOR_OUTLINE] of the 4 sides to show a stroke, not all 4 — a
     * real outline can be partly occluded (e.g. by a leading icon) without ceasing to be one.
     */
    private fun hasOutline(bitmap: Bitmap, textRect: Rect, backgroundColor: Int): Boolean {
        val marginX = (textRect.width() * OUTLINE_MAX_MARGIN_FRACTION).toInt()
            .coerceIn(MIN_OUTLINE_MARGIN_PX, MAX_OUTLINE_MARGIN_PX)
        val marginY = (textRect.height() * OUTLINE_MAX_MARGIN_FRACTION).toInt()
            .coerceIn(MIN_OUTLINE_MARGIN_PX, MAX_OUTLINE_MARGIN_PX)

        val left = (textRect.left - marginX).coerceAtLeast(0)
        val right = (textRect.right + marginX).coerceAtMost(bitmap.width)
        val top = (textRect.top - marginY).coerceAtLeast(0)
        val bottom = (textRect.bottom + marginY).coerceAtMost(bitmap.height)
        val bandWidth = right - left
        val bandHeight = bottom - top

        val topEdge = hasHorizontalStroke(bitmap, left, bandWidth, top, textRect.top, backgroundColor)
        val bottomEdge = hasHorizontalStroke(bitmap, left, bandWidth, textRect.bottom, bottom, backgroundColor)
        val leftEdge = hasVerticalStroke(bitmap, top, bandHeight, left, textRect.left, backgroundColor)
        val rightEdge = hasVerticalStroke(bitmap, top, bandHeight, textRect.right, right, backgroundColor)

        val edgesWithStroke = listOf(topEdge, bottomEdge, leftEdge, rightEdge).count { it }
        return edgesWithStroke >= MIN_EDGES_FOR_OUTLINE
    }

    /** True if any row in [bandTop, bandBottom) has a long-enough non-background run. */
    private fun hasHorizontalStroke(
        bitmap: Bitmap,
        left: Int,
        width: Int,
        bandTop: Int,
        bandBottom: Int,
        backgroundColor: Int
    ): Boolean {
        if (width <= 0 || bandTop >= bandBottom) return false
        val row = IntArray(width)
        for (y in bandTop until bandBottom) {
            bitmap.getPixels(row, 0, width, left, y, width, 1)
            if (hasContiguousRun(row, backgroundColor, OUTLINE_COVERAGE_FRACTION)) return true
        }
        return false
    }

    /** True if any column in [bandLeft, bandRight) has a long-enough non-background run. */
    private fun hasVerticalStroke(
        bitmap: Bitmap,
        top: Int,
        height: Int,
        bandLeft: Int,
        bandRight: Int,
        backgroundColor: Int
    ): Boolean {
        if (height <= 0 || bandLeft >= bandRight) return false
        val col = IntArray(height)
        for (x in bandLeft until bandRight) {
            bitmap.getPixels(col, 0, 1, x, top, 1, height)
            if (hasContiguousRun(col, backgroundColor, OUTLINE_COVERAGE_FRACTION)) return true
        }
        return false
    }

    private fun hasContiguousRun(pixels: IntArray, backgroundColor: Int, coverageFraction: Float): Boolean {
        val nonBackgroundCount = pixels.count { colorDistance(it, backgroundColor) > COLOR_DISTANCE_THRESHOLD }
        return nonBackgroundCount >= pixels.size * coverageFraction
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = ((a shr RED_SHIFT) and COLOR_CHANNEL_MASK) - ((b shr RED_SHIFT) and COLOR_CHANNEL_MASK)
        val dg = ((a shr GREEN_SHIFT) and COLOR_CHANNEL_MASK) - ((b shr GREEN_SHIFT) and COLOR_CHANNEL_MASK)
        val db = (a and COLOR_CHANNEL_MASK) - (b and COLOR_CHANNEL_MASK)
        return abs(dr) + abs(dg) + abs(db)
    }

    /**
     * Every [STATS_FLUSH_INTERVAL] checks, logs the average/max overhead this heuristic itself
     * adds on top of OCR — the actual number needed to judge whether always running it (rather
     * than, say, gating it on the blinking-cursor signal instead) is affordable. All calls land
     * on the same single-thread executor as the rest of [DefaultTextDetector], so no
     * synchronization is needed here.
     */
    private fun recordStats(durationNs: Long, isInputField: Boolean) {
        checkCount++
        totalDurationNs += durationNs
        if (durationNs > maxDurationNs) maxDurationNs = durationNs
        if (isInputField) inputFieldCount++

        if (checkCount % STATS_FLUSH_INTERVAL == 0) {
            val avgUs = (totalDurationNs / checkCount) / NANOS_PER_MICRO
            val maxUs = maxDurationNs / NANOS_PER_MICRO
            Log.i(
                CAPTURE_PIPELINE_LOG_TAG,
                "[InputFieldDetector] over $checkCount check(s): avg ${avgUs}us, max ${maxUs}us, " +
                    "$inputFieldCount classified as input field"
            )
        }
    }

    private companion object {
        private const val EDGE_MARGIN_FRACTION = 0.1f
        private const val UNDERLINE_SCAN_FRACTION = 0.6f
        private const val MIN_UNDERLINE_SCAN_ROWS = 6
        private const val MAX_UNDERLINE_SCAN_ROWS = 40

        private const val OUTLINE_MAX_MARGIN_FRACTION = 0.8f
        private const val MIN_OUTLINE_MARGIN_PX = 6
        private const val MAX_OUTLINE_MARGIN_PX = 60
        private const val OUTLINE_COVERAGE_FRACTION = 0.8f
        private const val MIN_EDGES_FOR_OUTLINE = 3

        private const val COLOR_DISTANCE_THRESHOLD = 30
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val COLOR_CHANNEL_MASK = 0xFF

        private const val STATS_FLUSH_INTERVAL = 25
        private const val NANOS_PER_MICRO = 1_000L
    }
}
