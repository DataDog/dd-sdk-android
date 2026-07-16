/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import androidx.annotation.AnyThread

/**
 * Detects whether a pixel-captured region ([bitmap]) contains text — see [DefaultTextDetector]
 * for the ML Kit-backed implementation and what it reports. Fire-and-forget: implementations
 * report their findings asynchronously (e.g. via Logcat) rather than through a return value or
 * callback, since nothing downstream of a capture currently acts on the result.
 */
internal interface TextDetector {

    /**
     * Kicks off text detection for [bitmap], identified by [nodeId] for logging.
     * [looksLikeBlinkingCursor] carries [BlinkingCursorTracker]'s cadence-based signal for this
     * same capture — a second, near-free vote on whether this region is an input field, alongside
     * whatever this detects from pixels. Must not block the calling thread — implementations are
     * expected to dispatch their own work.
     */
    @AnyThread
    fun detectText(bitmap: Bitmap, nodeId: Long, looksLikeBlinkingCursor: Boolean)

    /** Releases any resources held for detection (e.g. an ML model). No-op by default. */
    fun release() = Unit
}
