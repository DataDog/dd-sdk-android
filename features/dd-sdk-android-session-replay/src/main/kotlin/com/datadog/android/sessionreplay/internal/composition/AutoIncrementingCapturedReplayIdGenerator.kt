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
        currentId = if (currentId < Int.MAX_VALUE) currentId + 1 else 0
        return replayId
    }
}
