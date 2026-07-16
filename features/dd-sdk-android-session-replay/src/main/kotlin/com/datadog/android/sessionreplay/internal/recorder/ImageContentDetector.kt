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
 * Experimental, purely pixel-based: tells whether a pixel-captured bitmap contains any non-text
 * visual content — an icon, a photo, a drawing — beyond the OCR'd [textBounds] ML Kit already
 * found there.
 *
 * This exists specifically for `ImagePrivacy.MASK_ALL`: that level requires masking every
 * *image*, but text on its own isn't an image. `PixelCaptureEligibility` used to disable pixel
 * capture outright under `MASK_ALL` — the blunt, always-safe answer when nothing is known about a
 * region's content. Now that a capture's content actually *is* known (via [DefaultTextDetector]'s
 * OCR pass), a capture that turns out to be pure text can be shown as-is (once
 * `TextAndInputPrivacy` has already masked whatever it demands) instead of falling back to a
 * placeholder unconditionally — while one that also contains real image content still must, the
 * same judgment `PixelCaptureEligibility` made blanket, now made per-capture instead.
 *
 * Works by comparing the whole bitmap's non-background ("ink") pixel count against how much of
 * that ink falls *within* [textBounds] (padded slightly for anti-aliasing and glyph
 * descenders/ascenders extending past ML Kit's tight bounding box) — if the ink outside the text
 * regions is negligible, whatever's left really is just text.
 *
 * Unlike [InputFieldDetector]'s targeted edge-band scans, this reads every pixel in the bitmap —
 * there's no shortcut for "is there anything else here at all" the way there is for "is there a
 * stroke at this specific edge." [recordStats] tracks and periodically logs the actual overhead
 * this adds, since it's a materially larger scan and worth judging from real numbers, not
 * assumption, same as [InputFieldDetector]'s own overhead was.
 */
internal class ImageContentDetector {

    private var checkCount = 0
    private var totalDurationNs = 0L
    private var maxDurationNs = 0L
    private var nonTextContentCount = 0

    /**
     * True if [bitmap] contains non-background pixels outside every (padded) region in
     * [textBounds] beyond a small tolerance — i.e. there's something here besides the detected
     * text. Requires at least one entry in [textBounds] to compare against — a capture with no
     * OCR'd text at all ([DefaultTextDetector.Category.NO_TEXT]) is handled directly by the
     * caller instead: with zero text found, there's nothing this method's comparison would add
     * over just treating the whole capture as non-text content unconditionally.
     */
    @WorkerThread
    fun hasNonTextContent(bitmap: Bitmap, textBounds: List<Rect>): Boolean {
        require(textBounds.isNotEmpty()) { "hasNonTextContent requires at least one text bound" }
        val startNs = System.nanoTime()

        val backgroundColor = bitmap.getPixel(0, 0)
        val paddedBounds = textBounds.map { padRect(it, bitmap.width, bitmap.height) }
        val tolerancePx = (bitmap.width * bitmap.height * OUTSIDE_INK_TOLERANCE_FRACTION).toInt()
            .coerceAtLeast(MIN_OUTSIDE_INK_TOLERANCE_PX)

        val width = bitmap.width
        val row = IntArray(width)
        var outsideInkCount = 0

        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            val rectsOnRow = paddedBounds.filter { y >= it.top && y < it.bottom }
            for (x in 0 until width) {
                if (colorDistance(row[x], backgroundColor) <= COLOR_DISTANCE_THRESHOLD) continue
                if (rectsOnRow.none { x >= it.left && x < it.right }) {
                    outsideInkCount++
                }
            }
            if (outsideInkCount > tolerancePx) {
                // Early exit — the answer can no longer change, no need to scan the rest.
                recordStats(System.nanoTime() - startNs, true)
                return true
            }
        }

        recordStats(System.nanoTime() - startNs, false)
        return false
    }

    /** Pads [rect] by a fraction of its own size, for anti-aliasing/glyph overflow past OCR's tight box. */
    private fun padRect(rect: Rect, maxWidth: Int, maxHeight: Int): Rect {
        val paddingX = (rect.width() * TEXT_PADDING_FRACTION).toInt().coerceAtLeast(MIN_TEXT_PADDING_PX)
        val paddingY = (rect.height() * TEXT_PADDING_FRACTION).toInt().coerceAtLeast(MIN_TEXT_PADDING_PX)
        return Rect(
            (rect.left - paddingX).coerceAtLeast(0),
            (rect.top - paddingY).coerceAtLeast(0),
            (rect.right + paddingX).coerceAtMost(maxWidth),
            (rect.bottom + paddingY).coerceAtMost(maxHeight)
        )
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = ((a shr RED_SHIFT) and COLOR_CHANNEL_MASK) - ((b shr RED_SHIFT) and COLOR_CHANNEL_MASK)
        val dg = ((a shr GREEN_SHIFT) and COLOR_CHANNEL_MASK) - ((b shr GREEN_SHIFT) and COLOR_CHANNEL_MASK)
        val db = (a and COLOR_CHANNEL_MASK) - (b and COLOR_CHANNEL_MASK)
        return abs(dr) + abs(dg) + abs(db)
    }

    /**
     * Every [STATS_FLUSH_INTERVAL] checks, logs the average/max overhead this detector adds —
     * the number needed to judge whether a full-bitmap scan is affordable given how much rarer
     * `ImagePrivacy.MASK_ALL` is expected to be than the default. All calls land on the same
     * single-thread executor as the rest of [DefaultTextDetector], so no synchronization is
     * needed here.
     */
    private fun recordStats(durationNs: Long, hasNonTextContent: Boolean) {
        checkCount++
        totalDurationNs += durationNs
        if (durationNs > maxDurationNs) maxDurationNs = durationNs
        if (hasNonTextContent) nonTextContentCount++

        if (checkCount % STATS_FLUSH_INTERVAL == 0) {
            val avgUs = (totalDurationNs / checkCount) / NANOS_PER_MICRO
            val maxUs = maxDurationNs / NANOS_PER_MICRO
            Log.i(
                CAPTURE_PIPELINE_LOG_TAG,
                "[ImageContentDetector] over $checkCount check(s): avg ${avgUs}us, max ${maxUs}us, " +
                    "$nonTextContentCount found non-text content"
            )
        }
    }

    private companion object {
        private const val TEXT_PADDING_FRACTION = 0.15f
        private const val MIN_TEXT_PADDING_PX = 4

        private const val OUTSIDE_INK_TOLERANCE_FRACTION = 0.005f
        private const val MIN_OUTSIDE_INK_TOLERANCE_PX = 50

        private const val COLOR_DISTANCE_THRESHOLD = 30
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val COLOR_CHANNEL_MASK = 0xFF

        private const val STATS_FLUSH_INTERVAL = 25
        private const val NANOS_PER_MICRO = 1_000L
    }
}
