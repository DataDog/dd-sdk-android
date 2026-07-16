/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import androidx.annotation.UiThread

/**
 * Experimental: flags a node as likely showing a focused, blinking text-input cursor — purely
 * from *when* [PixelCapture] actually redraws it, no pixel inspection needed (unlike
 * [InputFieldDetector]). This is the cheapest of the three input-field signals, and — unlike
 * [InputFieldDetector], which needs at least one OCR'd text block to anchor its scan to — the
 * only one that can flag an *empty* focused field showing just a cursor and no text yet.
 *
 * [PixelCapture]'s own cache-trust window ([PixelCapture.MIN_REDRAW_INTERVAL_MS]) already means a
 * continuously-[android.view.View.isDirty] node gets a genuinely fresh `View.draw` call — but
 * *measured on-device* (against [PixelCaptureTestView][com.datadog.android.sample.sessionreplay.PixelCaptureTestView]'s
 * self-blinking cursor row, which invalidates every 500ms exactly), the actual spacing between
 * consecutive fresh captures for such a node settles to roughly 600-900ms, not literally 500ms —
 * snapshot-cycle scheduling and the cache-trust check both add their own delay on top of the raw
 * blink interval. It's also bursty: real fresh captures for the same node sometimes land only
 * 40-190ms apart (two decor-view listeners firing for the same underlying frame), which
 * [MIN_MEANINGFUL_INTERVAL_MS] treats as one logical redraw rather than as a broken streak.
 *
 * [minIntervalMs]/[maxIntervalMs] are narrowed to that measured ~600-900ms band specifically
 * because a wider band (originally 500-1400ms) turned out to also match at least two *other*
 * continuously-redrawing unmapped views on the same test screen (not cursors — never OCR'd any
 * text, and their own measured cadence clustered at a distinctly different ~1000-1250ms) as false
 * positives. The two bands don't overlap on-device, so [minIntervalMs]/[maxIntervalMs] sit in that
 * gap rather than at the blink cadence's own min/max, trading a little tolerance for genuine
 * cursor jitter for a real, measured margin against the nearest known non-cursor cadence. This
 * class doesn't re-derive the cadence itself; it just watches the *spacing between consecutive
 * fresh captures* [PixelCapture] was already going to do anyway (see
 * [PixelCapture.captureOrReuse]) and flags [streakThreshold] consecutive matches as "probably a
 * focused input field" — one matching gap in isolation could just as easily be coincidence (any
 * other reason a view invalidates once); a repeating streak at that cadence is the specific
 * signature only a blinking cursor produces.
 */
internal class BlinkingCursorTracker(
    private val minIntervalMs: Long = MIN_BLINK_INTERVAL_MS,
    private val maxIntervalMs: Long = MAX_BLINK_INTERVAL_MS,
    private val streakThreshold: Int = BLINK_STREAK_THRESHOLD
) {

    // @UiThread-only, same as every PixelCapture method that calls into this class — no
    // synchronization needed.
    private val trackedNodes = HashMap<Long, TrackedNode>()

    /**
     * Call once per fresh (non-cache-reused) capture, with [nodeId] and the capture's timestamp.
     * Returns true once [nodeId] has just shown [streakThreshold] consecutive matching-cadence
     * redraws in a row.
     */
    @UiThread
    fun recordFreshCapture(nodeId: Long, atMs: Long): Boolean {
        val previous = trackedNodes[nodeId]
        val interval = previous?.let { atMs - it.lastCaptureAtMs }

        // A near-duplicate capture (see the class doc) — neither confirms nor breaks a streak,
        // and isn't itself a new cadence sample, so lastCaptureAtMs is deliberately left
        // pointing at the earlier, meaningful sample rather than advanced to atMs.
        if (interval != null && interval < MIN_MEANINGFUL_INTERVAL_MS) {
            return checkNotNull(previous).streak >= streakThreshold
        }

        val isBlinkLikeInterval = interval != null && interval in minIntervalMs..maxIntervalMs
        val streak = if (isBlinkLikeInterval) checkNotNull(previous).streak + 1 else 0
        trackedNodes[nodeId] = TrackedNode(atMs, streak)
        return streak >= streakThreshold
    }

    /**
     * Clears all tracked state — call on navigation, alongside [PixelCapture]'s own cache clear,
     * since a nodeId isn't guaranteed to mean the same thing across screens (see that class's
     * doc).
     */
    @UiThread
    fun clear() {
        trackedNodes.clear()
    }

    private data class TrackedNode(val lastCaptureAtMs: Long, val streak: Int)

    internal companion object {
        // Below this, treat consecutive captures as one logical redraw rather than a new cadence
        // sample — see the class doc for the measured ~40-190ms duplicate-capture bursts this
        // filters out.
        internal const val MIN_MEANINGFUL_INTERVAL_MS = 300L

        // Measured real-cursor cadence: ~618-899ms. Measured nearest false-positive cadence (two
        // other continuously-redrawing, non-cursor unmapped views on the same test screen):
        // ~1004-1246ms. These sit in the gap between the two, not at the cursor's own min/max.
        internal const val MIN_BLINK_INTERVAL_MS = 550L
        internal const val MAX_BLINK_INTERVAL_MS = 950L
        internal const val BLINK_STREAK_THRESHOLD = 2
    }
}
