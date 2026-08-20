/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.privacy

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.AnyThread
import com.datadog.android.lint.InternalApi

/**
 * SDK-internal extension point: finds the bounding boxes of visible text in a rasterized pixel
 * capture, for the experimental composition-tree pipeline's pixel-fallback/privacy workstream
 * (`com.datadog.android.sessionreplay.internal.composition.PixelFallbackSnapshotProcessor`) to mask
 * before the bitmap can be hashed, encoded, and uploaded.
 *
 * Deliberately returns bounding boxes only - never recognized text, never the bitmap itself -
 * so masking policy and the "never persist recognized text" guarantee live entirely in core and are
 * enforced structurally: nothing on this boundary can carry a string through it. Not meant for
 * direct third-party implementation - the only real implementation lives in the optional
 * `dd-sdk-android-session-replay-text-detection` artifact, wired in via
 * `com.datadog.android.sessionreplay.textdetection.TextDetectionExtensionSupport`. This has to be a
 * public type only because Kotlin's `internal` visibility doesn't cross Gradle module boundaries,
 * and this module deliberately has no compile-time dependency on any on-device text-recognition
 * library. Every member is marked [InternalApi] rather than the interface itself, since that
 * annotation's own contract warns against applying it to interfaces (implementers would be flagged
 * as internal as a whole).
 */
fun interface TextDetector {

    /**
     * Finds every visible text region in [bitmap]. [onComplete] must be invoked exactly once, on
     * any thread, and must not block. Detector exceptions, timeouts, and cancellation must resolve
     * to [TextDetectionOutcome.Unavailable] rather than leaving [onComplete] uninvoked - the caller
     * fails closed (placeholder, never uploaded) whenever text presence can't be verified.
     */
    @InternalApi
    @AnyThread
    fun detectTextRegions(bitmap: Bitmap, onComplete: (TextDetectionOutcome) -> Unit)
}

/** The result of one [TextDetector.detectTextRegions] call. */
sealed interface TextDetectionOutcome {

    /**
     * Text was searched for successfully. [regions] may be empty - that means no text was found,
     * not that detection was skipped.
     */
    @InternalApi
    class Detected(val regions: List<Rect>) : TextDetectionOutcome

    /**
     * Text presence could not be verified (no detector configured, an exception, a timeout, or
     * cancellation). The caller must treat this exactly like "text may be present anywhere".
     */
    @InternalApi
    object Unavailable : TextDetectionOutcome
}
