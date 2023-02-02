/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.sampling

import java.security.SecureRandom

/**
 * [Sampler] with the given constant sample rate.
 */
class RateBasedSampler(internal val sampleRate: Float) : Sampler {

    /**
     * Creates a new instance of [RateBasedSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(sampleRate: Double) : this(sampleRate.toFloat())

    private val random by lazy { SecureRandom() }

    /** @inheritDoc */
    override fun sample(): Boolean {
        return when (sampleRate) {
            0f -> false
            1f -> true
            else -> random.nextFloat() <= sampleRate
        }
    }

    /** @inheritDoc */
    override fun getSamplingRate(): Float? {
        return sampleRate
    }
}
