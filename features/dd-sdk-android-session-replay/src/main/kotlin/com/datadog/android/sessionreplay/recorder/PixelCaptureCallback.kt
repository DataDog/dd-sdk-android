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
 * Manages pixel-capture requests for Session Replay's unmapped views.
 *
 * The only implementation ([PixelCapture], wired via `CompositionTreeBuilder`) is only ever fed
 * captures from `PixelCaptureFallbackMapper`, which always sets [isolationClipRect] to
 * [isolationView]'s *entire* own bounds — so every registered capture today is one whole view.
 * [isolationClipRect] is a separate field from [isolationView] mainly so a future caller could
 * clip to a sub-rectangle of a larger view (e.g. one element within a container) without needing
 * a different contract; nothing does that today.
 *
 * Implementations capture [isolationView] by calling `View.draw` clipped to [isolationClipRect] —
 * isolated from anything drawn on top of it. Captures are evaluated post-traversal in
 * `processPendingCaptures`; if the per-cycle capture budget is exceeded before a pending capture is
 * reached, it is replaced via [WireframeSlot] with a placeholder instead of being captured.
 *
 * @see MappingContext.pixelCaptureCallback
 */
interface PixelCaptureCallback {

    /**
     * Registers a pending capture for [isolationView], identified by [nodeId]. Called during SR
     * traversal.
     *
     * Calls [asyncJobStatusCallback.jobStarted] immediately to gate the queue item. The actual
     * capture is deferred until `processPendingCaptures` runs post-traversal.
     *
     * @param nodeId Stable identifier for [isolationView], used as the cache key.
     * @param dpBounds Bounds in density-normalised DP — used to place a placeholder if this
     * capture times out before it can be resolved.
     * @param isolationView The view to call `View.draw` on.
     * @param isolationClipRect Clip rect within [isolationView]'s coordinate space.
     * @param wireframe Stub `ImageWireframe` (isEmpty=true) to populate on success.
     * @param wireframeSlot Callback used to swap [wireframe] out for a placeholder if the
     * capture budget is exceeded before this capture can be processed.
     * @param asyncJobStatusCallback Used to call [AsyncJobStatusCallback.jobFinished].
     */
    fun registerPendingCapture(
        nodeId: Long,
        dpBounds: GlobalBounds,
        isolationView: View,
        isolationClipRect: Rect,
        wireframe: MobileSegment.Wireframe.ImageWireframe,
        wireframeSlot: WireframeSlot,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        // Default no-op — implementations register the capture for deferred processing.
        asyncJobStatusCallback.jobFinished()
    }
}

/**
 * Allows a deferred pixel-capture pipeline to replace a stub wireframe already embedded in
 * the SR node tree — e.g. swapping it for a [MobileSegment.Wireframe.PlaceholderWireframe]
 * when the per-cycle capture budget runs out before the capture can be processed.
 */
fun interface WireframeSlot {
    /**
     * Replaces the wireframe originally registered via [PixelCaptureCallback.registerPendingCapture]
     * with [wireframe].
     */
    fun replace(wireframe: MobileSegment.Wireframe)
}
