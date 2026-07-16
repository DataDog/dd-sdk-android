/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import androidx.annotation.AnyThread
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.recorder.PixelCaptureCallback

/**
 * Detects whether a pixel-captured region ([bitmap]) contains text — see [DefaultTextDetector]
 * for the ML Kit-backed implementation and what it reports.
 *
 * Not fire-and-forget: [onComplete] must be called **exactly once** per [detectText] call, with
 * a [CaptureOutcome] — [CaptureOutcome.Upload] carrying the same [bitmap] instance (mutated in
 * place with a black rectangle over any region [TextAndInputPrivacy] requires masking, or
 * untouched if none does) to hand to the resource resolver for upload, or
 * [CaptureOutcome.ReplaceWithPlaceholder] when [ImagePrivacy] requires the whole capture be
 * replaced instead. This is why detection has to finish before the resolver ever sees the bitmap,
 * rather than running alongside it as a side channel purely for logging (as it did before masking
 * existed): a bitmap already uploaded can't be retroactively redacted, and a placeholder decided
 * on afterward is too late to matter.
 */
internal interface TextDetector {

    /**
     * Kicks off text detection for [bitmap], identified by [nodeId] for logging.
     * [looksLikeBlinkingCursor] carries [BlinkingCursorTracker]'s cadence-based signal for this
     * same capture — a second, near-free vote on whether this region is an input field, alongside
     * whatever this detects from pixels. [textAndInputPrivacy] and [imagePrivacy] are the
     * already-resolved privacy levels for this capture (see
     * [PixelCaptureCallback.registerPendingCapture]'s privacy-aware overload) — implementations
     * use them to decide *whether* and *what* to mask, rather than assuming a fixed policy. Must
     * not block the calling thread — implementations are expected to dispatch their own work, and
     * to call [onComplete] exactly once when done, on any thread.
     */
    @AnyThread
    fun detectText(
        bitmap: Bitmap,
        nodeId: Long,
        looksLikeBlinkingCursor: Boolean,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        onComplete: (CaptureOutcome) -> Unit
    )

    /** Releases any resources held for detection (e.g. an ML model). No-op by default. */
    fun release() = Unit
}

/** What [PixelCapture] should do with a capture once [TextDetector.detectText] has decided. */
internal sealed interface CaptureOutcome {

    /** Upload [bitmap] (already masked as needed) via the resource resolver, as normal. */
    data class Upload(val bitmap: Bitmap) : CaptureOutcome

    /**
     * Replace this capture's wireframe with a placeholder instead of uploading anything —
     * [ImagePrivacy.MASK_ALL] demands this once the capture is known to contain non-text visual
     * content (see [ImageContentDetector]), the same as [PixelCaptureEligibility] already does
     * for an *entire* region up front when nothing about its content is known yet.
     */
    object ReplaceWithPlaceholder : CaptureOutcome
}
