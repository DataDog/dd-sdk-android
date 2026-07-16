/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
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
 * field.
 *
 * Two independent, complementary signals decide [Category.HAS_INPUT_FIELD] — see
 * [categorizeAndLog] for how they combine:
 * - [BlinkingCursorTracker]'s cadence-based signal (near-free, passed in as
 *   [detectText]'s `looksLikeBlinkingCursor` — this class doesn't compute it), which only fires
 *   for a *focused* field but works even with zero OCR'd text.
 * - [InputFieldDetector]'s pixel-based signal, which catches an unfocused field too but needs at
 *   least one OCR'd text block (a value or a placeholder) to anchor its scan to.
 *
 * [Category.HAS_INPUT_FIELD] with at least one OCR'd text block is also where
 * [PixelCaptureFallbackMapper]'s doc-documented gap actually gets closed, not just described: raw
 * pixel capture has no idea what a region contains, so — unlike the semantic mapper chain — it
 * couldn't selectively mask just the sensitive text within one. [maskRegions] closes that by
 * painting a solid black rectangle directly onto the bitmap, before it's ever handed off for
 * upload — see [TextDetector]'s doc for why that ordering, not a separate overlay wireframe, is
 * how this works. *Which* regions get masked depends on [textAndInputPrivacy] — see
 * [categorizeAndLog] — not a single fixed policy: `MASK_ALL` masks every detected block
 * regardless of category, since that level requires masking all text, not just fields; the other
 * two levels mask only input-field regions (all of them, pixel capture having no way to tell a
 * *sensitive* input field from an ordinary one apart from OCR'd content and layout alone).
 *
 * [imagePrivacy] governs a separate decision: whether to upload the capture at all, versus
 * replacing it wholesale with a placeholder (see [CaptureOutcome]). `MASK_ALL` requires masking
 * every *image* — but text on its own isn't an image, so [ImageContentDetector] decides whether
 * this specific capture contains anything beyond the text `TextAndInputPrivacy` already handled;
 * only if it does does this fall back to a placeholder, the same call `PixelCaptureEligibility`
 * used to make blanket for `MASK_ALL`, now made per-capture with actual content in hand.
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
    private val inputFieldDetector: InputFieldDetector = InputFieldDetector(),
    private val imageContentDetector: ImageContentDetector = ImageContentDetector()
) : TextDetector {

    /** The three buckets a pixel-captured region is classified into — see the class doc. */
    internal enum class Category { NO_TEXT, HAS_TEXT, HAS_INPUT_FIELD }

    private val maskPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    @AnyThread
    override fun detectText(
        bitmap: Bitmap,
        nodeId: Long,
        looksLikeBlinkingCursor: Boolean,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        onComplete: (CaptureOutcome) -> Unit
    ) {
        executor.executeSafe(DETECT_TEXT_OPERATION_NAME, internalLogger) {
            processBitmap(bitmap, nodeId, looksLikeBlinkingCursor, textAndInputPrivacy, imagePrivacy, onComplete)
        }
    }

    @WorkerThread
    private fun processBitmap(
        bitmap: Bitmap,
        nodeId: Long,
        looksLikeBlinkingCursor: Boolean,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        onComplete: (CaptureOutcome) -> Unit
    ) {
        // The bitmap is owned by the capture pipeline for the duration of this call (see
        // PixelCapture's doc — nothing recycles it on this path), but guarding here costs nothing
        // and InputImage.fromBitmap has undefined behavior on a recycled one. Every early return
        // below still calls onComplete — see TextDetector's doc: skipping it would mean this
        // capture never reaches the resource resolver at all, not just that it goes up unmasked.
        if (bitmap.isRecycled) {
            onComplete(CaptureOutcome.Upload(bitmap))
            return
        }

        val image = try {
            InputImage.fromBitmap(bitmap, ROTATION_DEGREES_NONE)
        } catch (e: IllegalArgumentException) {
            onComplete(CaptureOutcome.Upload(bitmap))
            return
        }

        recognizer.process(image)
            .addOnSuccessListener(executor) { visionText ->
                categorizeAndLog(
                    bitmap,
                    nodeId,
                    visionText,
                    looksLikeBlinkingCursor,
                    textAndInputPrivacy,
                    imagePrivacy,
                    onComplete
                )
            }
            .addOnFailureListener(executor) {
                // On-device recognition failing (e.g. the bundled model isn't ready yet on this
                // device) just means this cycle contributes no signal and uploads unmasked —
                // self-correcting the next time this node is freshly captured, no different from
                // any other dropped capture in this experimental pipeline.
                onComplete(CaptureOutcome.Upload(bitmap))
            }
    }

    /**
     * [looksLikeBlinkingCursor] is checked first and, if true, short-circuits straight to
     * [Category.HAS_INPUT_FIELD] without ever calling [InputFieldDetector] — both because it's
     * the only signal that can catch an empty field with zero OCR'd text, and because skipping
     * the pixel-scan heuristic once cadence alone has already answered the question is free
     * performance back for [InputFieldDetector]'s own budget. Note that in that specific case
     * (blink-triggered, zero OCR'd text) there's nothing for [maskRegions] to black out — an
     * empty field showing just a cursor has no content to redact yet.
     *
     * [textAndInputPrivacy] only affects *masking* ([regionsToMask]), never [category] — the
     * category is a description of what was found, independent of what privacy demands be done
     * about it. [imagePrivacy] is independent again — it decides the final [CaptureOutcome], after
     * masking (if any) has already been painted on.
     */
    @WorkerThread
    private fun categorizeAndLog(
        bitmap: Bitmap,
        nodeId: Long,
        visionText: Text,
        looksLikeBlinkingCursor: Boolean,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        onComplete: (CaptureOutcome) -> Unit
    ) {
        val blocksWithBounds = visionText.textBlocks.mapNotNull { block -> block.boundingBox?.let { it to block } }
        val matchedRegions = if (blocksWithBounds.isEmpty()) {
            emptyList()
        } else {
            inputFieldDetector.findInputFieldRegions(bitmap, blocksWithBounds.map { it.first })
        }

        val category = when {
            looksLikeBlinkingCursor -> Category.HAS_INPUT_FIELD
            blocksWithBounds.isEmpty() -> Category.NO_TEXT
            matchedRegions.isNotEmpty() -> Category.HAS_INPUT_FIELD
            else -> Category.HAS_TEXT
        }

        // MASK_ALL requires masking every detected block regardless of category — plain text
        // included, not just fields. The other two levels (MASK_SENSITIVE_INPUTS,
        // MASK_ALL_INPUTS) both only care about inputs; pixel capture has no way to tell a
        // *sensitive* input apart from an ordinary one (that distinction lives in Android's
        // InputType flags, invisible to raw pixels), so both are treated the same: mask every
        // input-field region, precisely (matchedRegions) when the pixel heuristic found the
        // specific block(s) that look like a field — sparing unrelated static text sharing the
        // same capture — or, when only the blinking-cursor signal (a whole-view signal, not
        // attributable to one block) is why this is HAS_INPUT_FIELD, every block in this capture,
        // on the reasoning that any text sharing a view with a focused input cursor is very
        // likely that field's own content.
        val regionsToMask = when {
            textAndInputPrivacy == TextAndInputPrivacy.MASK_ALL -> blocksWithBounds.map { it.first }
            matchedRegions.isNotEmpty() -> matchedRegions
            category == Category.HAS_INPUT_FIELD -> blocksWithBounds.map { it.first }
            else -> emptyList()
        }
        if (regionsToMask.isNotEmpty()) {
            maskRegions(bitmap, regionsToMask)
        }

        logCategory(nodeId, category, blocksWithBounds)

        val requiresPlaceholder = imagePrivacy == ImagePrivacy.MASK_ALL &&
            hasNonTextContent(bitmap, category, blocksWithBounds)
        val outcome = if (requiresPlaceholder) {
            CaptureOutcome.ReplaceWithPlaceholder
        } else {
            CaptureOutcome.Upload(bitmap)
        }
        onComplete(outcome)
    }

    /**
     * [Category.NO_TEXT] is treated as non-text content unconditionally — no text at all means
     * there's nothing to compare against, so [ImageContentDetector] (which requires at least one
     * text bound) isn't even called; a blank capture and an actual photo look identical from
     * here, and the safe assumption is the same one [PixelCaptureEligibility] used to make
     * blanket for every `MASK_ALL` region. Otherwise, delegates to [ImageContentDetector] to check
     * whether anything besides the OCR'd text is present.
     */
    @WorkerThread
    private fun hasNonTextContent(
        bitmap: Bitmap,
        category: Category,
        blocksWithBounds: List<Pair<Rect, Text.TextBlock>>
    ): Boolean {
        if (category == Category.NO_TEXT) return true
        return imageContentDetector.hasNonTextContent(bitmap, blocksWithBounds.map { it.first })
    }

    /**
     * Paints a solid black rectangle over each of [regions], directly onto [bitmap] — the same
     * bitmap [PixelCapture] is about to hand to the resource resolver for upload, so this is the
     * actual pixel data that ends up recorded, not a separate overlay a player has to know to
     * composite. [regions] are already in this bitmap's own pixel coordinate space (straight from
     * ML Kit's bounding boxes), so no coordinate conversion is needed here — unlike a wireframe
     * overlay would require, converting into the surrounding dp/screen coordinate space.
     */
    @WorkerThread
    private fun maskRegions(bitmap: Bitmap, regions: List<Rect>) {
        val canvas = Canvas(bitmap)
        regions.forEach { canvas.drawRect(it, maskPaint) }
    }

    /**
     * One Logcat line for [category], plus — for [Category.HAS_TEXT] and
     * [Category.HAS_INPUT_FIELD] — one further line per detected text block: [nodeId] (to
     * correlate with the pixel-capture pipeline's own health logging), the block's bounding box
     * (also, for [Category.HAS_INPUT_FIELD], exactly the region [maskRegions] just blacked out),
     * and how many characters it contains — **never the recognized text itself**. Logging the
     * actual content back out to Logcat would defeat the entire point of this experiment: it's
     * precisely the kind of on-screen text this pipeline is trying to locate (and, now, mask),
     * not surface in the clear through a different channel.
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
