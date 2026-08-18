/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal fun GlobalBounds.toCaptured() = CapturedBounds(x, y, width, height)

internal data class CapturedFullSnapshot(
    val timestamp: Long,
    val scope: RumViewIdentityScope,
    val root: CapturedLayer?,
    val layers: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)
