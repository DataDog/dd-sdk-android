/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.sampling

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import kotlin.math.pow
import kotlin.math.sqrt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RateBasedSamplerTest {

    private lateinit var testedSampler: RateBasedSampler

    @FloatForgery(min = 0f, max = 100f)
    var randomSampleRate: Float = 0.0f

    @BeforeEach
    fun `set up`() {
        testedSampler = RateBasedSampler(randomSampleRate)
    }

    @Test
    fun `the sampler will sample the values based on the fixed sample rate`() {
        // Given
        val dataSize = 1000
        val testRepeats = 100
        val computedSampleRates = mutableListOf<Double>()

        // When
        repeat(testRepeats) {
            var validated = 0
            repeat(dataSize) {
                val isValid = if (testedSampler.sample()) 1 else 0
                validated += isValid
            }
            val computedSampleRate = (validated.toDouble() / dataSize.toDouble()) * 100
            computedSampleRates.add(computedSampleRate)
        }
        val sampleRateMean = computedSampleRates.sum().div(computedSampleRates.size)
        val variance = computedSampleRates
            .sumOf { (sampleRateMean.minus(it)).pow(2) }
            .div(computedSampleRates.size)
        val deviation = sqrt(variance)

        // Then
        assertThat(sampleRateMean).isCloseTo(
            randomSampleRate.toDouble(),
            Offset.offset(deviation)
        )
    }

    @Test
    fun `the sampler will sample the values based on the dynamic sample rate`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRateA: Float,
        @FloatForgery(min = 0f, max = 100f) fakeSampleRateB: Float
    ) {
        // Given
        val dataSize = 1000
        val testRepeats = 100
        val computedSampleRates = mutableListOf<Double>()
        var invocationCounter = 0
        testedSampler = RateBasedSampler {
            invocationCounter++
            if (invocationCounter.mod(dataSize) <= dataSize / 2) {
                fakeSampleRateA
            } else {
                fakeSampleRateB
            }
        }

        // When
        repeat(testRepeats) {
            var validated = 0
            repeat(dataSize) {
                val isValid = if (testedSampler.sample()) 1 else 0
                validated += isValid
            }
            val computedSampleRate = (validated.toDouble() / dataSize.toDouble()) * 100
            computedSampleRates.add(computedSampleRate)
        }
        val sampleRateMean = computedSampleRates.sum().div(computedSampleRates.size)
        val variance = computedSampleRates
            .sumOf { (sampleRateMean.minus(it)).pow(2) }
            .div(computedSampleRates.size)
        val deviation = sqrt(variance)

        // Then
        assertThat(sampleRateMean).isCloseTo(
            (fakeSampleRateA + fakeSampleRateB).toDouble() / 2,
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
    fun `when sample rate is 100 all values will pass`() {
        testedSampler = RateBasedSampler(100.0f)

        var validated = 0
        val dataSize = 10

        repeat(dataSize) {
            val isValid = if (testedSampler.sample()) 1 else 0
            validated += isValid
        }

        assertThat(validated).isEqualTo(dataSize)
    }

    @Test
    fun `when sample rate is below 0 it is normalized to 0`(
        @FloatForgery(max = 0f) fakeSampleRate: Float
    ) {
        // Given
        testedSampler = RateBasedSampler(fakeSampleRate)

        // When
        val effectiveSampleRate = testedSampler.getSampleRate()

        // Then
        assertThat(effectiveSampleRate).isZero
    }

    @Test
    fun `when sample rate is above 100 it is normalized to 100`(
        @FloatForgery(min = 100.01f) fakeSampleRate: Float
    ) {
        // Given
        testedSampler = RateBasedSampler(fakeSampleRate)

        // When
        val effectiveSampleRate = testedSampler.getSampleRate()

        // Then
        assertThat(effectiveSampleRate).isEqualTo(100f)
    }
}
