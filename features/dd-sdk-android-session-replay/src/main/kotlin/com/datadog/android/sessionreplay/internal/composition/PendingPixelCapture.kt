/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity

/**
 * A raster capture taken synchronously during the main-thread traversal, still awaiting async
 * text-region masking and resource registration (see `PixelFallbackSnapshotProcessor`).
 * [wireframeIdentity] is the already-minted, already-referenced identity of the
 * `CapturedWireframe.Pixel` this capture will resolve; [ownerIdentity] is that wireframe's owning
 * layer, needed only if resolution instead has to downgrade to a fresh
 * `CapturedWireframe.PrivacyPlaceholder` identity and fix up the owner's child reference.
 * [skipsTextDetection] skips text-region detection and resolves the raster as an image, as the legacy
 * recorder does for a drawable. It is only for captures that no text detector is there to check;
 * any other capture could have readable text baked into it, so it fails closed to a placeholder
 * when a detector cannot verify it.
 */
internal data class PendingPixelCapture(
    val wireframeIdentity: CapturedIdentity,
    val ownerIdentity: CapturedIdentity,
    val bitmap: Bitmap,
    val skipsTextDetection: Boolean = false
)

/** Where a mapper deposits a [PendingPixelCapture] it can't resolve synchronously. */
internal fun interface PendingPixelCaptureSink {
    fun register(capture: PendingPixelCapture)

    companion object {
        val NoOp = PendingPixelCaptureSink { }
    }
}
