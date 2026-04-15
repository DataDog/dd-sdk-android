/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sampling

/**
 * Utilities for deterministic sampling calculations.
 */
object DeterministicSampling {

    private const val SAMPLE_ALL_RATE: Float = 100f

    /**
     * Returns the effective sampling rate when a child feature's [featureSampleRate] is applied
     * on top of a parent session's [sessionSampleRate].
     *
     * The combined rate represents the probability that both the session AND the child feature
     * are sampled in, preserving the deterministic guarantee: any session that passes the combined
     * threshold is guaranteed to have also passed the session-level threshold.
     *
     * @param sessionSampleRate The RUM session sampling rate (0–100).
     * @param featureSampleRate The child feature sampling rate (0–100).
     * @return The effective combined rate, clamped to [0, 100].
     */
    fun combinedSampleRate(sessionSampleRate: Float, featureSampleRate: Float): Float =
        (sessionSampleRate * featureSampleRate / SAMPLE_ALL_RATE)
            .coerceIn(0f, SAMPLE_ALL_RATE)
}
