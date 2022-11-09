/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.sampling

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Random
import kotlin.math.pow
import kotlin.math.sqrt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RateBasedSamplerTest {

    lateinit var testedSampler: RateBasedSampler

    private var randomSampleRate: Float = 0.0f

    @BeforeEach
    fun `set up`() {
        randomSampleRate = Random().nextFloat()
        testedSampler = RateBasedSampler(randomSampleRate)
    }

    @Test
    fun `the sampler will sample the values based on the sample rate`() {
        val dataSize = 1000
        val testRepeats = 100
        val computedSamplingRates = mutableListOf<Double>()
        repeat(testRepeats) {
            var validated = 0
            repeat(dataSize) {
                val isValid = if (testedSampler.sample()) 1 else 0
                validated += isValid
            }
            val computedSamplingRate = validated.toDouble() / dataSize.toDouble()
            computedSamplingRates.add(computedSamplingRate)
        }
        val samplingRateMean = computedSamplingRates.sum().div(computedSamplingRates.size)
        val variance = computedSamplingRates
            .map { (samplingRateMean.minus(it)).pow(2) }
            .sum()
            .div(computedSamplingRates.size)
        val deviation = sqrt(variance)

        assertThat(samplingRateMean).isCloseTo(
            randomSampleRate.toDouble(),
            Offset.offset(deviation)
        )
    }

    @Test
    fun `when sample rate is 0 all values will be dropped`() {
        testedSampler = RateBasedSampler(0.0f)

        var validated = 0
        val dataSize = 10

        repeat(dataSize) {
            val isValid = if (testedSampler.sample()) 1 else 0
            validated += isValid
        }

        assertThat(validated).isEqualTo(0)
    }

    @Test
    fun `when sample rate is 1 all values will pass`() {
        testedSampler = RateBasedSampler(1.0f)

        var validated = 0
        val dataSize = 10

        repeat(dataSize) {
            val isValid = if (testedSampler.sample()) 1 else 0
            validated += isValid
        }

        assertThat(validated).isEqualTo(dataSize)
    }
}
