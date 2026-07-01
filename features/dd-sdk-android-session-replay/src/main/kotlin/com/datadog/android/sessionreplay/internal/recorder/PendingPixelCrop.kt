/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * A deferred pixel-capture request, registered during SR traversal and evaluated
 * post-traversal by [PixelCopyCapture.processPendingCrops].
 *
 * Carries two capture strategies so [processPendingCrops] can pick the right one
 * after the full Node tree (and therefore all wireframe bounds) are known:
 *
 * **No overlay detected → PixelCopy path:**
 * Crop [windowRect] from the stored full-window bitmap. Full fidelity — parent alpha,
 * transforms, and hardware effects are all captured because the bitmap came from the
 * composited GPU output.
 *
 * **Overlay detected → isolation path:**
 * Call `View.draw(canvas)` on [isolationView] clipped to [isolationClipRect]. No
 * overlay contamination — only the view's own drawing is captured. Parent effects
 * are not captured, but the overlay was already covering that area anyway.
 */
internal data class PendingPixelCrop(
    /** Stable identifier — used to de-duplicate and as the wireframe ID. */
    val nodeId: Long,
    /** Window-pixel rect for the PixelCopy crop (matches the stored bitmap's coordinate space). */
    val windowRect: Rect,
    /** Density-normalised DP bounds for overlap comparison against other wireframe bounds. */
    val dpBounds: GlobalBounds,
    /** The view whose `View.draw` is called in the isolation path. */
    val isolationView: View,
    /** Clip rect within [isolationView]'s coordinate space for the isolation path. */
    val isolationClipRect: Rect,
    /** Stub wireframe — populated with a resource ID once the chosen capture succeeds. */
    val wireframe: MobileSegment.Wireframe.ImageWireframe,
    /** Used to call [AsyncJobStatusCallback.jobFinished] after the pipeline completes. */
    val asyncJobStatusCallback: AsyncJobStatusCallback
)
