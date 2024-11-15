/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import com.datadog.android.internal.profiler.BenchmarkSpan
import com.datadog.android.internal.profiler.withinBenchmarkSpan

private const val ATTRIBUTE_CONTAINER = "attribute.container"
private const val ATTRIBUTE_TYPE_KEY = "attribute.type"
private const val ATTRIBUTE_TYPE_COMPOSE_VALUE = "compose"

/**
 * A wrap function of [withinComposeBenchmarkSpan] dedicated to session replay span recording.
 */
internal inline fun <T : Any?> withinComposeBenchmarkSpan(
    spanName: String,
    isContainer: Boolean = false,
    block: BenchmarkSpan.() -> T
): T {
    return withinBenchmarkSpan(
        spanName,
        mapOf(
            ATTRIBUTE_CONTAINER to isContainer.toString(),
            ATTRIBUTE_TYPE_KEY to ATTRIBUTE_TYPE_COMPOSE_VALUE
        ),
        block
    )
}
