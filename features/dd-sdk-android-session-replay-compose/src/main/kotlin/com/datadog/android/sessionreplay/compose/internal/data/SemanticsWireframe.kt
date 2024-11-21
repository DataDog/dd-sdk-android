/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import com.datadog.android.sessionreplay.model.MobileSegment

internal data class SemanticsWireframe(
    val wireframes: List<MobileSegment.Wireframe>,
    val uiContext: UiContext?
)
