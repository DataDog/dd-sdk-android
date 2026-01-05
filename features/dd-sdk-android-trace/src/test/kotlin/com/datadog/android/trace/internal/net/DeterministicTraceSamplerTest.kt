/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DeterministicTraceSamplerTest {

    private lateinit var testedSampler: DeterministicTraceSampler

    @Mock
    lateinit var mockSpanContext: DatadogSpanContext

    private lateinit var fakeSpans: List<DatadogSpan>

    @BeforeEach
    fun `set up`(forge: Forge) {
        val listSize = forge.anInt(256, 1024)
        fakeSpans = forge.aList(listSize) {
            val traceId = mock<DatadogTraceId> {
                on { toLong() } doReturn aLong()
            }
            val context = mock<DatadogSpanContext> {
                on { this.traceId } doReturn traceId
                on { tags } doReturn emptyMap()
            }
            mock<DatadogSpan> {
                on { context() } doReturn context
            }
        }
    }

    @RepeatedTest(32)
    fun `M sample spans based on rate W sample() {float constructor}`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        testedSampler = DeterministicTraceSampler(fakeSampleRate)
        var sampledIn = 0

        // When
        fakeSpans.forEach {
            if (testedSampler.sample(it)) {
                sampledIn++
            }
        }

        // Then
        val offset = 2.5f * fakeSpans.size
        assertThat(sampledIn.toFloat()).isCloseTo(
            fakeSpans.size * fakeSampleRate / 100f,
            Offset.offset(offset)
        )
    }

    @RepeatedTest(32)
    fun `M sample spans based on rate W sample() {double constructor}`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        testedSampler = DeterministicTraceSampler(fakeSampleRate.toDouble())
        var sampledIn = 0

        // When
        fakeSpans.forEach {
            if (testedSampler.sample(it)) {
                sampledIn++
            }
        }

        // Then
        val offset = 2.5f * fakeSpans.size
        assertThat(sampledIn.toFloat()).isCloseTo(
            fakeSpans.size * fakeSampleRate / 100f,
            Offset.offset(offset)
        )
    }

    @RepeatedTest(32)
    fun `M sample spans based on rate W sample() {provider constructor}`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        testedSampler = DeterministicTraceSampler { fakeSampleRate }
        var sampledIn = 0

        // When
        fakeSpans.forEach {
            if (testedSampler.sample(it)) {
                sampledIn++
            }
        }

        // Then
        val offset = 2.5f * fakeSpans.size
        assertThat(sampledIn.toFloat()).isCloseTo(
            fakeSpans.size * fakeSampleRate / 100f,
            Offset.offset(offset)
        )
    }

    @Test
    fun `M drop all spans W sample() {rate is 0}`() {
        // Given
        testedSampler = DeterministicTraceSampler(0f)
        var sampledIn = 0

        // When
        fakeSpans.forEach {
            if (testedSampler.sample(it)) {
                sampledIn++
            }
        }

        // Then
        assertThat(sampledIn).isEqualTo(0)
    }

    @Test
    fun `M keep all spans W sample() {rate is 100}`() {
        // Given
        testedSampler = DeterministicTraceSampler(100f)
        var sampledIn = 0

        // When
        fakeSpans.forEach {
            if (testedSampler.sample(it)) {
                sampledIn++
            }
        }

        // Then
        assertThat(sampledIn).isEqualTo(fakeSpans.size)
    }

    @Test
    fun `M return sample rate W getSampleRate()`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        testedSampler = DeterministicTraceSampler(fakeSampleRate)

        // When
        val result = testedSampler.getSampleRate()

        // Then
        assertThat(result).isEqualTo(fakeSampleRate)
    }

    @RepeatedTest(16)
    fun `M return consistent result W sample() {same span}`(
        @LongForgery fakeTraceIdLong: Long
    ) {
        // Given
        testedSampler = DeterministicTraceSampler(50f)
        val traceId = mock<DatadogTraceId> {
            on { toLong() } doReturn fakeTraceIdLong
        }
        val context = mock<DatadogSpanContext> {
            on { this.traceId } doReturn traceId
            on { tags } doReturn emptyMap()
        }
        val span = mock<DatadogSpan> {
            on { context() } doReturn context
        }

        // When
        val results = (1..10).map { testedSampler.sample(span) }

        // Then
        assertThat(results.distinct()).hasSize(1)
    }
}
