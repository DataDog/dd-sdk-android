/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.sampling

import java.security.SecureRandom

internal class RateBasedSampler(internal val sampleRate: Float) : Sampler {

    constructor(sampleRate: Double) : this(sampleRate.toFloat())

    private val random by lazy { SecureRandom() }

    override fun sample(): Boolean {
        return when (sampleRate) {
            0f -> false
            1f -> true
            else -> random.nextFloat() <= sampleRate
        }
    }

    override fun getSamplingRate(): Float? {
        return sampleRate
    }
}
