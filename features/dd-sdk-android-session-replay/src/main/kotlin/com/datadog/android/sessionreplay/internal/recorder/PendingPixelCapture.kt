/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * A deferred pixel-capture request, registered during SR traversal and evaluated
 * post-traversal by [PixelCapture.processPendingCaptures]: `View.draw(canvas)` is called on
 * [isolationView] clipped to [isolationClipRect] — isolated from anything drawn on top of it.
 */
internal data class PendingPixelCapture(
    /** Stable identifier — used to de-duplicate, as the wireframe ID, and as the cache key. */
    val nodeId: Long,
    /** Density-normalised DP bounds — used to place a placeholder if this capture times out. */
    val dpBounds: GlobalBounds,
    /** The view whose `View.draw` is called. */
    val isolationView: View,
    /** Clip rect within [isolationView]'s coordinate space. */
    val isolationClipRect: Rect,
    /** Stub wireframe — populated with a resource ID once capture succeeds. */
    val wireframe: MobileSegment.Wireframe.ImageWireframe,
    /** Used to swap [wireframe] for a placeholder if the capture budget runs out first. */
    val wireframeSlot: WireframeSlot,
    /** Used to call [AsyncJobStatusCallback.jobFinished] after the pipeline completes. */
    val asyncJobStatusCallback: AsyncJobStatusCallback
)
