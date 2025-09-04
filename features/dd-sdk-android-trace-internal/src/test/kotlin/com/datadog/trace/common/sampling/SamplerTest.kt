/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.trace.common.sampling

import com.datadog.trace.api.Config
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.api.sampling.SamplingMechanism
import com.datadog.trace.bootstrap.instrumentation.api.SamplerConstants
import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
class SamplerTest {

    private lateinit var mockConfig: Config

    @BeforeEach
    fun `set up`() {
        mockConfig = mock<Config> {
            on { traceSampleRate } doReturn null
            on { traceSamplingRules } doReturn null
            on { traceSamplingServiceRules } doReturn null
            on { isV2CompatibilityEnabled } doReturn false
            on { isPrioritySamplingEnabled } doReturn false
            on { traceSamplingOperationRules } doReturn null
        }
    }

    @Test
    fun `M return RuleBasedTraceSampler W forConfig {v2 compatible, sampleRate provided}`(
        @DoubleForgery(min = 0.1, max = 1.0) fakeSampleRate: Double
    ) {
        // Given
        whenever(mockConfig.isV2CompatibilityEnabled).thenReturn(true)
        whenever(mockConfig.traceSampleRate).thenReturn(fakeSampleRate)

        // When
        val actual = Sampler.Builder.forConfig(mockConfig, null)

        // Then
        assertThat(actual).isInstanceOf(RuleBasedTraceSampler::class.java)
    }

    @Test
    fun `M return RateByServiceTraceSampler W forConfig {v2 incompatible, sampleRate provided}`(
        @DoubleForgery(min = 0.1, max = 1.0) fakeSampleRate: Double
    ) {
        // Given
        whenever(mockConfig.traceSampleRate).thenReturn(fakeSampleRate)

        // When
        val actual = Sampler.Builder.forConfig(mockConfig, null)

        // Then
        check(actual is RateByServiceTraceSampler)
        assertThat(actual.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M return AllSampler W forConfig {v2 incompatible, sampleRate not provided}`() {
        // When
        val actual = Sampler.Builder.forConfig(mockConfig, null)

        // Then
        assertThat(actual).isInstanceOf(AllSampler::class.java)
    }

    @Test
    fun `M return ForcePrioritySampler W forConfig {priority sampling, prioritySamplingForce = KEEP}`() {
        // Given
        whenever(mockConfig.isPrioritySamplingEnabled).thenReturn(true)
        whenever(mockConfig.prioritySamplingForce).thenReturn(SamplerConstants.KEEP)

        // When
        val actual = Sampler.Builder.forConfig(mockConfig, null)

        // Then
        check(actual is ForcePrioritySampler)
        assertThat(actual.prioritySampling).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())
        assertThat(actual.samplingMechanism).isEqualTo(SamplingMechanism.DEFAULT.toInt())
    }

    @Test
    fun `M return ForcePrioritySampler W forConfig {priority sampling, prioritySamplingForce = DROP}`() {
        // Given
        whenever(mockConfig.isPrioritySamplingEnabled).thenReturn(true)
        whenever(mockConfig.prioritySamplingForce).thenReturn(SamplerConstants.DROP)

        // When
        val actual = Sampler.Builder.forConfig(mockConfig, null)

        // Then
        check(actual is ForcePrioritySampler)
        assertThat(actual.prioritySampling).isEqualTo(PrioritySampling.SAMPLER_DROP.toInt())
        assertThat(actual.samplingMechanism).isEqualTo(SamplingMechanism.DEFAULT.toInt())
    }

    @Test
    fun `M return RateByServiceTraceSampler W forConfig {priority sampling, prioritySamplingForce is null}`() {
        // Given
        whenever(mockConfig.isPrioritySamplingEnabled).thenReturn(true)

        // When
        val actual = Sampler.Builder.forConfig(mockConfig, null)

        // Then
        assertThat(actual).isInstanceOf(RateByServiceTraceSampler::class.java)
    }

    @Test
    fun `M return AllSampler W forConfig {config is null}`() {
        // When
        val actual = Sampler.Builder.forConfig(null, null)

        // Then
        assertThat(actual).isInstanceOf(AllSampler::class.java)
    }
}
