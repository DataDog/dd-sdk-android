/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.utils.GlobalBounds

/** Values owned by a capture, with no live UI objects retained for processing. */
internal sealed interface CapturedSnapshotInput

internal data class CapturedNativeSnapshot(
    val timestamp: Long,
    val scope: RumViewIdentityScope,
    val windows: List<CapturedNativeWindow>
) : CapturedSnapshotInput

/** Preorder copy; each parent index refers to an earlier entry, or -1 for the window root. */
internal data class CapturedNativeWindow(
    val screenDensity: Float,
    val views: List<CapturedNativeView>,
    val isDrawingOrderReliable: Boolean = true
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
    val composition: CompositionHostDecomposeResult? = null
)
