/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.metrics

/**
 * Sampling rates for Method Call telemetry.
 * @param rate the rate to sample at (between 0 and 100).
 */
enum class MethodCallSamplingRate(val rate: Float) {
    ALL(rate = 100.0f),
    HIGH(rate = 10.0f),
    MEDIUM(rate = 1.0f),
    DEFAULT(rate = 0.1f),
    REDUCED(rate = 0.01f),
    RARE(rate = 0.001f)
}
