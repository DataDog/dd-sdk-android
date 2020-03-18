package com.datadog.android.core.internal.sampling

import java.util.Random

internal class RateBasedSampler(internal val sampleRate: Float) : Sampler {
    private val random by lazy { Random() }

    override fun sample(): Boolean {
        if (sampleRate == 0f) {
            return false
        }
        if (sampleRate == 1f) {
            return true
        }
        return random.nextFloat() <= sampleRate
    }
}
