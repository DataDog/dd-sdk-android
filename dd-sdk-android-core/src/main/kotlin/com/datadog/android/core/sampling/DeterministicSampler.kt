/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.sampling

import androidx.annotation.FloatRange
import com.datadog.android.api.InternalLogger

/**
 * [Sampler] with the given sample rate using a deterministic algorithm for a stable
 * sampling decision across sources.
 *
 * @param T the type of items to sample.
 * @param idConverter a lambda converting the input item into a stable numerical identifier
 * @param sampleRateProvider Provider for the sample rate value which will be called each time
 * the sampling decision needs to be made. All the values should be on the scale [0;100].
 */
open class DeterministicSampler<T : Any>(
    private val idConverter: (T) -> ULong,
    private val sampleRateProvider: () -> Float
) : Sampler<T> {

    /**
     * Creates a new instance lof [DeterministicSampler] with the given sample rate.
     *
     * @param idConverter a lambda converting the input item into a stable numerical identifier
     * @param sampleRate Sample rate to use.
     */
    constructor(
        idConverter: (T) -> ULong,
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Float
    ) : this(idConverter, { sampleRate })

    /**
     * Creates a new instance of [DeterministicSampler] with the given sample rate.
     *
     * @param idConverter a lambda converting the input item into a stable numerical identifier
     * @param sampleRate Sample rate to use.
     */
    constructor(
        idConverter: (T) -> ULong,
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Double
    ) : this(idConverter, sampleRate.toFloat())

    /** @inheritDoc */
    override fun sample(item: T): Boolean {
        val sampleRate = getSampleRate()

        return when {
            sampleRate >= SAMPLE_ALL_RATE -> true
            sampleRate <= 0f -> false
            else -> {
                val hash = idConverter(item) * SAMPLER_HASHER
                val threshold = (MAX_ID.toDouble() * sampleRate / SAMPLE_ALL_RATE).toULong()
                hash < threshold
            }
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

        // Good number for Knuth hashing (large, prime, fit in 64 bit long)
        private const val SAMPLER_HASHER: ULong = 1111111111111111111u

        private const val MAX_ID: ULong = 0xFFFFFFFFFFFFFFFFUL
    }
}
