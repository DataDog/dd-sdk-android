/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData

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
    val views: List<CapturedNativeView>
)

internal data class CapturedNativeView(
    val identity: CapturedIdentity,
    val parentIndex: Int,
    val bounds: CapturedBounds,
    val alpha: Double,
    val mapping: CapturedViewData,
    val clipToBounds: Boolean,
    val explicitClip: CapturedBounds?,
    val childPaddingClip: CapturedBounds?
)
