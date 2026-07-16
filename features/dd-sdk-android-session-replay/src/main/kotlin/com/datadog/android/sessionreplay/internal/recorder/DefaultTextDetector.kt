/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

/**
 * Experimental: runs ML Kit's on-device (Latin) text recognizer over freshly-drawn pixel
 * captures — see [PixelCapture] for the single call site, right after a real [Bitmap] is drawn
 * (never on a cache-reused capture, so a region already known to be static isn't re-scanned every
 * cycle) — purely to scope which unmapped views actually contain text, and whether that text (or,
 * via [BlinkingCursorTracker] alone, even an empty field with no text yet) sits inside an input
 * field. [PixelCaptureFallbackMapper]'s doc already calls out the gap this targets: raw pixel
 * capture has no idea what a region contains, so — unlike the semantic mapper chain — it can't
 * selectively mask just the sensitive text within one; this is the first step toward closing that,
 * not the fix itself.
 *
 * Two independent, complementary signals decide [Category.HAS_INPUT_FIELD] — see
 * [categorizeAndLog] for how they combine:
 * - [BlinkingCursorTracker]'s cadence-based signal (near-free, passed in as
 *   [detectText]'s `looksLikeBlinkingCursor` — this class doesn't compute it), which only fires
 *   for a *focused* field but works even with zero OCR'd text.
 * - [InputFieldDetector]'s pixel-based signal, which catches an unfocused field too but needs at
 *   least one OCR'd text block (a value or a placeholder) to anchor its scan to.
 *
 * Buckets every capture into exactly one of [Category] and logs it — see [logCategory] for
 * exactly what that line does and, just as importantly, does **not** include.
 *
 * Detection is dispatched onto [executor] end to end — both the (non-trivial) [InputImage] setup
 * and the result callback — via [Task.addOnSuccessListener]'s executor overload, since ML Kit's
 * own [TextRecognizer.process] Task still runs its recognition work on its own internal worker
 * regardless of caller thread, but its listeners default to the main-thread `Looper` unless an
 * executor is given explicitly.
 */
internal class DefaultTextDetector(
    private val executor: Executor,
    private val internalLogger: InternalLogger,
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
    private val inputFieldDetector: InputFieldDetector = InputFieldDetector()
) : TextDetector {

    /** The three buckets a pixel-captured region is classified into — see the class doc. */
    internal enum class Category { NO_TEXT, HAS_TEXT, HAS_INPUT_FIELD }

    @AnyThread
    override fun detectText(bitmap: Bitmap, nodeId: Long, looksLikeBlinkingCursor: Boolean) {
        executor.executeSafe(DETECT_TEXT_OPERATION_NAME, internalLogger) {
            processBitmap(bitmap, nodeId, looksLikeBlinkingCursor)
        }
    }

    @WorkerThread
    private fun processBitmap(bitmap: Bitmap, nodeId: Long, looksLikeBlinkingCursor: Boolean) {
        // The bitmap is owned by the capture pipeline for the duration of this call (see
        // PixelCapture's doc — nothing recycles it on this path), but guarding here costs nothing
        // and InputImage.fromBitmap has undefined behavior on a recycled one.
        if (bitmap.isRecycled) return

        val image = try {
            InputImage.fromBitmap(bitmap, ROTATION_DEGREES_NONE)
        } catch (e: IllegalArgumentException) {
            return
        }

        recognizer.process(image)
            .addOnSuccessListener(executor) { visionText ->
                categorizeAndLog(bitmap, nodeId, visionText, looksLikeBlinkingCursor)
            }
        // No failure listener: on-device recognition failing (e.g. the bundled model isn't ready
        // yet on this device) just means this cycle contributes no signal — self-correcting the
        // next time this node is freshly captured, no different from any other dropped capture in
        // this experimental pipeline.
    }

    /**
     * [looksLikeBlinkingCursor] is checked first and, if true, short-circuits straight to
     * [Category.HAS_INPUT_FIELD] without ever calling [InputFieldDetector] — both because it's
     * the only signal that can catch an empty field with zero OCR'd text, and because skipping
     * the pixel-scan heuristic once cadence alone has already answered the question is free
     * performance back for [InputFieldDetector]'s own budget.
     */
    @WorkerThread
    private fun categorizeAndLog(bitmap: Bitmap, nodeId: Long, visionText: Text, looksLikeBlinkingCursor: Boolean) {
        val blocksWithBounds = visionText.textBlocks.mapNotNull { block -> block.boundingBox?.let { it to block } }

        val category = when {
            looksLikeBlinkingCursor -> Category.HAS_INPUT_FIELD
            blocksWithBounds.isEmpty() -> Category.NO_TEXT
            inputFieldDetector.looksLikeInputField(
                bitmap,
                blocksWithBounds.map { it.first }
            ) -> Category.HAS_INPUT_FIELD
            else -> Category.HAS_TEXT
        }
        logCategory(nodeId, category, blocksWithBounds)
    }

    /**
     * One Logcat line for [category], plus — for [Category.HAS_TEXT] and
     * [Category.HAS_INPUT_FIELD] — one further line per detected text block: [nodeId] (to
     * correlate with the pixel-capture pipeline's own health logging), the block's bounding box
     * (the eventual target for a redaction rectangle), and how many characters it contains —
     * **never the recognized text itself**. Logging the actual content back out to Logcat would
     * defeat the entire point of this experiment: it's precisely the kind of on-screen text this
     * pipeline can't yet mask that it's trying to locate, not surface in the clear through a
     * different channel.
     */
    @WorkerThread
    private fun logCategory(
        nodeId: Long,
        category: Category,
        blocksWithBounds: List<Pair<Rect, Text.TextBlock>> = emptyList()
    ) {
        Log.i(CAPTURE_PIPELINE_LOG_TAG, "[TextDetector] node=$nodeId category=$category")
        blocksWithBounds.forEach { (bounds, block) ->
            Log.i(
                CAPTURE_PIPELINE_LOG_TAG,
                "[TextDetector] node=$nodeId bounds=$bounds length=${block.text.length}"
            )
        }
    }

    override fun release() {
        recognizer.close()
    }

    private companion object {
        private const val DETECT_TEXT_OPERATION_NAME = "detectText"
        private const val ROTATION_DEGREES_NONE = 0
    }
}
