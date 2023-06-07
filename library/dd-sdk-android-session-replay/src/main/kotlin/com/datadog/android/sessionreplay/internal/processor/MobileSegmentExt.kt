/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.model.MobileSegment

internal fun MobileSegment.Wireframe.copy(clip: MobileSegment.WireframeClip?):
    MobileSegment.Wireframe {
    return when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> this.copy(clip = clip)
        is MobileSegment.Wireframe.TextWireframe -> this.copy(clip = clip)
        is MobileSegment.Wireframe.ImageWireframe -> this.copy(clip = clip)
    }
}
