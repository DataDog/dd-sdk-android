/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import androidx.annotation.AnyThread
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.recorder.PixelCaptureCallback

/**
 * Detects whether a pixel-captured region ([bitmap]) contains text — see [DefaultTextDetector]
 * for the ML Kit-backed implementation and what it reports.
 *
 * Not fire-and-forget: [onComplete] must be called **exactly once** per [detectText] call, with
 * the same [bitmap] instance — mutated in place with a black rectangle over any region privacy
 * requires masking, or untouched if none does — before [PixelCapture] hands it to the resource
 * resolver for upload. This is why detection has to finish before the resolver ever sees the
 * bitmap, rather than running alongside it as a side channel purely for logging (as it did before
 * masking existed): a bitmap already uploaded can't be retroactively redacted.
 */
internal interface TextDetector {

    /**
     * Kicks off text detection for [bitmap], identified by [nodeId] for logging.
     * [looksLikeBlinkingCursor] carries [BlinkingCursorTracker]'s cadence-based signal for this
     * same capture — a second, near-free vote on whether this region is an input field, alongside
     * whatever this detects from pixels. [textAndInputPrivacy] is the already-resolved privacy
     * level for this capture (see [PixelCaptureCallback.registerPendingCapture]'s privacy-aware
     * overload) — implementations use it to decide *whether* and *what* to mask, rather than
     * assuming a fixed policy. Must not block the calling thread — implementations are expected to
     * dispatch their own work, and to call [onComplete] exactly once when done, on any thread.
     */
    @AnyThread
    fun detectText(
        bitmap: Bitmap,
        nodeId: Long,
        looksLikeBlinkingCursor: Boolean,
        textAndInputPrivacy: TextAndInputPrivacy,
        onComplete: (Bitmap) -> Unit
    )

    /** Releases any resources held for detection (e.g. an ML model). No-op by default. */
    fun release() = Unit
}
