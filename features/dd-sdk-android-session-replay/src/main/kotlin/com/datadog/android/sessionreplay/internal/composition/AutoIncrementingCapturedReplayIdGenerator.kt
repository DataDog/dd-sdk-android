/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal class AutoIncrementingCapturedReplayIdGenerator(
    initialId: Long = 0
) : CapturedReplayIdGenerator {
    private var currentId = initialId

    @Synchronized
    override fun next(): Long {
        val replayId = currentId
        // Wraps at Int.MAX_VALUE, not Long.MAX_VALUE, on purpose: currentId is Long only for the
        // arithmetic LAYER_ID_OFFSET + currentId needs, not because the wrap bound itself should be
        // wider. See LAYER_ID_OFFSET's KDoc - a raw id past 31 bits would spill into the namespace
        // bits a wireframe id shifts into above it.
        currentId = if (currentId < Int.MAX_VALUE) currentId + 1 else 0
        return replayId
    }
}
