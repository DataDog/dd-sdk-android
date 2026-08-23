/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.lint.InternalApi

/**
 * A raster capture taken synchronously during the main-thread traversal, still awaiting async
 * text-region masking and resource registration (see `PixelFallbackSnapshotProcessor`).
 * [wireframeIdentity] is the already-minted, already-referenced identity of the
 * `CapturedWireframe.Pixel` this capture will resolve; [ownerIdentity] is that wireframe's owning
 * layer, needed only if resolution instead has to downgrade to a fresh
 * `CapturedWireframe.PrivacyPlaceholder` identity and fix up the owner's child reference.
 * [isTextFree] skips text-region detection entirely for captures that are structurally
 * guaranteed not to contain text - a `TextView`/`Button`'s background-drawable-only rasterization
 * (its text is always captured separately, as its own `CapturedWireframe.Text`), not an arbitrary
 * view's full-content screenshot, which could genuinely have readable text baked into it and must
 * still fail closed to a placeholder when a detector can't verify otherwise.
 *
 * Public (rather than `internal`) so it can cross the Gradle module boundary into the optional
 * `dd-sdk-android-session-replay-compose` module, the same reason
 * `com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest` is
 * public - Kotlin `internal` visibility doesn't cross module boundaries. Not meant for direct
 * third-party construction.
 *
 * @param wireframeIdentity the already-minted, already-referenced identity of the
 * `CapturedWireframe.Pixel` this capture will resolve
 * @param ownerIdentity that wireframe's owning layer, needed only if resolution instead has to
 * downgrade to a fresh `CapturedWireframe.PrivacyPlaceholder` identity and fix up the owner's
 * child reference
 * @param bitmap the raster content captured synchronously during traversal
 * @param isTextFree true for captures structurally guaranteed not to contain text, skipping
 * text-region detection entirely
 */
@InternalApi
data class PendingPixelCapture(
    val wireframeIdentity: CapturedIdentity,
    val ownerIdentity: CapturedIdentity,
    val bitmap: Bitmap,
    val isTextFree: Boolean = false
)

/**
 * Where a mapper deposits a [PendingPixelCapture] it can't resolve synchronously. Not annotated
 * [InternalApi] itself - that annotation's own contract warns against applying it to interfaces,
 * since implementers would be flagged as internal as a whole (see
 * `CompositionHostDecomposer`'s own doc for the same precedent) - so each member is annotated
 * instead.
 */
@Suppress("PackageNameVisibility") // Can't mark it as @InternalApi as it would apply to implementations as well
fun interface PendingPixelCaptureSink {

    /** Registers [capture] to be resolved asynchronously. */
    @InternalApi
    fun register(capture: PendingPixelCapture)

    companion object {
        /** A [PendingPixelCaptureSink] that discards every registration. */
        @InternalApi
        val NoOp: PendingPixelCaptureSink = PendingPixelCaptureSink { }
    }
}
