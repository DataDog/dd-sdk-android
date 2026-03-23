/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.ddApiClient

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
internal suspend fun <T : Any> poll(
    block: suspend () -> T,
    predicate: (T) -> Boolean,
    interval: Duration,
    timeout: Duration
): T? {
    val mark = TimeSource.Monotonic.markNow()

    while (mark.elapsedNow() < timeout) {
        val result = block()
        if (predicate(result)) return result
        delay(interval)
    }
    return null
}
