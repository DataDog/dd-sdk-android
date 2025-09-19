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
    /**
     * Sample all.
     */
    ALL(rate = 100.0f),

    /**
     * Sample 10% of the time.
     */
    HIGH(rate = 10.0f),

    /**
     * Sample 1% of the time.
     */
    MEDIUM(rate = 1.0f),

    /**
     * Sample 0.1% of the time.
     */
    LOW(rate = 0.1f),

    /**
     * Sample 0.01% of the time.
     */
    REDUCED(rate = 0.01f),

    /**
     * Sample 0.001% of the time.
     */
    RARE(rate = 0.001f)
}
