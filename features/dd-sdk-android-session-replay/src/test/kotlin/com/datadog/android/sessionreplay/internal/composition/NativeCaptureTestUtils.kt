/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import com.datadog.android.sessionreplay.TouchPrivacy

internal fun WindowCopyResult.buildWindow(context: CaptureGenerationContext): WindowWalkResult = when (this) {
    WindowCopyResult.Aborted -> WindowWalkResult.Aborted
    WindowCopyResult.Filtered -> WindowWalkResult.Filtered
    is WindowCopyResult.Present -> {
        val built = CapturedNativeSnapshotBuilder().buildWindow(window, context)
        if (built == null) {
            WindowWalkResult.Aborted
        } else {
            WindowWalkResult.Present(built.rootLayer, built.layers, built.wireframes, touchOverrideAreas)
        }
    }
}

internal fun CapturedSnapshotInput?.buildSnapshot(
    context: CaptureGenerationContext
): CapturedFullSnapshot? = when (this) {
    null -> null
    is CapturedFullSnapshot -> this
    is CapturedNativeSnapshot -> CapturedNativeSnapshotBuilder().build(this, context)
}

internal sealed interface WindowWalkResult {
    data class Present(
        val rootLayer: CapturedLayer,
        val layers: List<CapturedLayer>,
        val wireframes: List<CapturedWireframe>,
        val touchOverrideAreas: Map<Rect, TouchPrivacy>
    ) : WindowWalkResult
    object Filtered : WindowWalkResult
    object Aborted : WindowWalkResult
}
