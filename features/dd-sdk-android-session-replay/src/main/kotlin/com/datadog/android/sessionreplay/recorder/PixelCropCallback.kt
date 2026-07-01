/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.graphics.Rect
import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * Manages pixel-capture requests for Session Replay's unmapped view regions.
 *
 * Implementations maintain a full-window [android.view.PixelCopy] bitmap (updated once per
 * SR snapshot cycle) and decide per-region whether to use it or fall back to isolated
 * [View.draw]-based capture:
 *
 * **PixelCopy path (default, no overlay detected):** crops [windowRect] from the stored
 * composited bitmap. Full fidelity — parent alpha, transforms, and hardware effects included.
 *
 * **Isolation path (overlay detected):** calls `View.draw` on [isolationView] clipped to
 * [isolationClipRect]. No overlay contamination — only the view's own drawing is captured.
 *
 * Both paths are evaluated post-traversal in `processPendingCrops` once all wireframe bounds
 * are known, enabling the correct overlap check.
 *
 * @see MappingContext.pixelCropCallback
 */
interface PixelCropCallback {

    /**
     * Registers a pending capture for [nodeId]'s region. Called during SR traversal.
     *
     * Calls [asyncJobStatusCallback.jobStarted] immediately to gate the queue item.
     * The actual capture decision (PixelCopy vs isolation) is deferred until
     * `processPendingCrops` runs post-traversal with the full wireframe bounds picture.
     *
     * @param nodeId Stable identifier for the region (semantics node ID or view ID).
     * @param windowRect Region in window-pixel coordinates for the PixelCopy crop.
     * @param dpBounds Same region in density-normalised DP for the overlap check.
     * @param isolationView The view to call `View.draw` on in the isolation path.
     * @param isolationClipRect Clip rect within [isolationView]'s coordinate space.
     * @param wireframe Stub `ImageWireframe` (isEmpty=true) to populate on success.
     * @param asyncJobStatusCallback Used to call [AsyncJobStatusCallback.jobFinished].
     */
    fun registerPendingCrop(
        nodeId: Long,
        windowRect: Rect,
        dpBounds: GlobalBounds,
        isolationView: View,
        isolationClipRect: Rect,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        // Default no-op — implementations register the crop for deferred processing.
        asyncJobStatusCallback.jobFinished()
    }
}
