/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.sampling

import androidx.annotation.FloatRange
import com.datadog.android.api.InternalLogger
import java.security.SecureRandom

/**
 * [Sampler] with the given sample rate which can be fixed or dynamic.
 *
 * @param T the type of items to sample.
 * @param sampleRateProvider Provider for the sample rate value which will be called each time
 * the sampling decision needs to be made. All the values should be on the scale [0;100].
 */
open class RateBasedSampler<T : Any>(private val sampleRateProvider: () -> Float) : Sampler<T> {

    /**
     * Creates a new instance of [RateBasedSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float) : this({ sampleRate })

    /**
     * Creates a new instance of [RateBasedSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(@FloatRange(from = 0.0, to = 100.0) sampleRate: Double) : this(sampleRate.toFloat())

    private val random by lazy { SecureRandom() }

    /** @inheritDoc */
    @Suppress("MagicNumber")
    override fun sample(item: T): Boolean {
        return when (val sampleRate = getSampleRate()) {
            0f -> false
            SAMPLE_ALL_RATE -> true
            else -> random.nextFloat() * 100 <= sampleRate
        }
    }

    /** @inheritDoc */
    override fun getSampleRate(): Float {
        val rawSampleRate = sampleRateProvider()
        return if (rawSampleRate < 0f) {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "Sample rate value provided $rawSampleRate is below 0, setting it to 0." }
            )
            0f
        } else if (rawSampleRate > SAMPLE_ALL_RATE) {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "Sample rate value provided $rawSampleRate is above 100, setting it to 100." }
            )
            SAMPLE_ALL_RATE
        } else {
            rawSampleRate
        }
    }

    private companion object {
        const val SAMPLE_ALL_RATE = 100f
    }
}
