/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.PendingViewMapping
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.utils.GlobalBounds

/** Values owned by a capture, with no live UI objects retained for processing. */
internal sealed interface CapturedSnapshotInput

internal data class CapturedNativeSnapshot(
    val timestamp: Long,
    val scope: RumViewIdentityScope,
    val windows: List<CapturedNativeWindow>,
    val pendingPixelCaptures: List<PendingPixelCapture> = emptyList(),
    val identityFactory: CapturedIdentityFactory? = null,
    val hiddenEmbeddedWireframes: List<CapturedWireframe> = emptyList()
) : CapturedSnapshotInput

/** Preorder copy; each parent index refers to an earlier entry, or -1 for the window root. */
internal data class CapturedNativeWindow(
    val screenDensity: Float,
    val views: List<CapturedNativeView>,
    val isDrawingOrderReliable: Boolean = true,
    val pendingPixelCaptures: List<PendingPixelCapture> = emptyList(),
    val pendingMappings: List<PendingViewMapping> = emptyList(),
    val dimAmount: Double = 0.0
)

internal data class CapturedNativeView(
    val identity: CapturedIdentity,
    val parentIndex: Int,
    val bounds: CapturedBounds,
    val alpha: Double,
    val viewData: CapturedViewData,
    val clipToBounds: Boolean,
    val explicitClip: CapturedBounds?,
    val childPaddingClip: CapturedBounds?,
    // Unrounded geometry from the existing bounds read; never inferred from density-normalized values.
    val pixelBounds: GlobalBounds? = null,
    val pixelClip: GlobalBounds? = null,
    val pixelChildPaddingClip: GlobalBounds? = null,
    val isGeometryReliable: Boolean = false,
    val kind: CapturedLayerKind = if (parentIndex < 0) CapturedLayerKind.WINDOW_ROOT else CapturedLayerKind.NATIVE_VIEW,
    val composition: CompositionHostDecomposeResult? = null,
    val foreground: CapturedForeground? = null,
    val viewScale: Float = 1f,
    val permanentId: String? = null,
    val modifiers: List<CapturedModifier> = emptyList()
)

/** Owned foreground values, with a distinct identity from the view background. */
internal data class CapturedForeground(
    val identity: CapturedIdentity,
    val wireframe: CapturedWireframe
)
