/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * A no-op [InteropViewCallback] for pipelines that never encounter Jetpack Compose interop
 * views — e.g. the composition-tree pipeline, which only walks plain [android.view.View]s.
 */
internal class NoOpInteropViewCallback : InteropViewCallback {
    override fun map(view: View, mappingContext: MappingContext): List<MobileSegment.Wireframe> {
        return emptyList()
    }
}
