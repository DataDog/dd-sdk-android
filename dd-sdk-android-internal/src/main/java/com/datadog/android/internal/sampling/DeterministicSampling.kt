/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sampling

private const val SAMPLE_ALL_RATE: Float = 100f
private const val SAMPLER_HASHER: ULong = 1111111111111111111u
private const val MAX_ID: ULong = 0xFFFFFFFFFFFFFFFFUL

/**
 * Computes a deterministic sampling decision based on the given sample rate and identifier.
 *
 * @param sampleRate the sample rate in the range [0, 100].
 * @param id a stable numerical identifier derived from the item being sampled.
 * @return true if the item should be sampled, false otherwise.
 */
fun computeSamplingDecision(sampleRate: Float, id: ULong): Boolean {
    return when {
        sampleRate >= SAMPLE_ALL_RATE -> true
        sampleRate <= 0f -> false
        else -> {
            val hash = id * SAMPLER_HASHER
            val threshold = (MAX_ID.toDouble() * sampleRate / SAMPLE_ALL_RATE).toULong()
            hash < threshold
        }
    }
}
