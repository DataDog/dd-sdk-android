/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import com.datadog.android.sessionreplay.utils.GlobalBounds

internal data class BackgroundInfo(
    val globalBounds: GlobalBounds = GlobalBounds(0L, 0L, 0L, 0L),
    val color: Long? = null,
    val cornerRadius: Float = 0f
)
