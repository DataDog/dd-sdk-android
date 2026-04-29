/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.core.sampling.Sampler
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionRebasedSamplerTest {

    // region sample()

    @Test
    fun `M use rebased rate W sample() { session_sample_rate tag present }`(forge: Forge) {
        // Given
        val fakeTraceRate = forge.aFloat(min = 0f, max = 100f)
        val fakeSessionRate = forge.aFloat(min = 0f, max = 99.99f)
        val expectedRate = fakeTraceRate * fakeSessionRate / 100f

        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)

        val spans = createSpans(forge, sessionSampleRate = fakeSessionRate)

        // When
        val sampledCount = spans.count { testedSampler.sample(it) }

        // Then
        val expectedCount = spans.size * expectedRate / 100f
        assertThat(sampledCount.toFloat()).isCloseTo(expectedCount, Offset.offset(spans.size * 0.1f))
    }

    @Test
    fun `M use raw rate W sample() { session_sample_rate tag is 100 }`(forge: Forge) {
        // Given
        val fakeTraceRate = forge.aFloat(min = 0f, max = 100f)
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)

        val spans = createSpans(forge, sessionSampleRate = 100f)

        // When
        val sampledCount = spans.count { testedSampler.sample(it) }

        // Then
        val expectedCount = spans.size * fakeTraceRate / 100f
        assertThat(sampledCount.toFloat()).isCloseTo(expectedCount, Offset.offset(spans.size * 0.1f))
    }

    @Test
    fun `M use raw rate W sample() { session_sample_rate tag absent }`(forge: Forge) {
        // Given
        val fakeTraceRate = forge.aFloat(min = 0f, max = 100f)
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)

        val spans = createSpans(forge, sessionSampleRate = null)

        // When
        val sampledCount = spans.count { testedSampler.sample(it) }

        // Then
        val expectedCount = spans.size * fakeTraceRate / 100f
        assertThat(sampledCount.toFloat()).isCloseTo(expectedCount, Offset.offset(spans.size * 0.1f))
    }

    @Test
    fun `M sample none W sample() { session_sample_rate is 0 }`(forge: Forge) {
        // Given
        val fakeTraceRate = forge.aFloat(min = 1f, max = 100f)
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)

        val spans = createSpans(forge, sessionSampleRate = 0f)

        // When
        val sampledCount = spans.count { testedSampler.sample(it) }

        // Then
        assertThat(sampledCount).isEqualTo(0)
    }

    @Test
    fun `M sample nothing W sample() { session_sample_rate is negative }`(forge: Forge) {
        // Given
        val fakeTraceRate = forge.aFloat(min = 0f, max = 100f)
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)

        val fakeNegativeRate = -forge.aFloat(min = 1f, max = 100f)
        val spans = createSpans(forge, sessionSampleRate = fakeNegativeRate)

        // When
        val sampledCount = spans.count { testedSampler.sample(it) }

        // Then
        assertThat(sampledCount).isEqualTo(0)
    }

    @Test
    fun `M delegate to custom sampler W sample() { delegate is not DeterministicTraceSampler }`(
        forge: Forge
    ) {
        // Given
        val mockDelegate = mock<Sampler<DatadogSpan>>()
        whenever(mockDelegate.sample(org.mockito.kotlin.any())).thenReturn(true)
        val testedSampler = SessionRebasedSampler(mockDelegate)

        val span = createSpan(forge, sessionSampleRate = 50f)

        // When
        val result = testedSampler.sample(span)

        // Then
        assertThat(result).isTrue()
        verify(mockDelegate).sample(span)
    }

    // endregion

    // region getSampleRate()

    @Test
    fun `M return delegate raw rate W getSampleRate()`(
        @FloatForgery(min = 0f, max = 100f) fakeTraceRate: Float
    ) {
        // Given
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)

        // When
        val result = testedSampler.getSampleRate()

        // Then
        assertThat(result).isEqualTo(fakeTraceRate)
    }

    // endregion

    // region getSampleRate(span)

    @Test
    fun `M return rebased rate W getSampleRate(span) { session tag present }`(
        @FloatForgery(min = 0f, max = 100f) fakeTraceRate: Float,
        @FloatForgery(min = 0f, max = 99.99f) fakeSessionRate: Float,
        forge: Forge
    ) {
        // Given
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)
        val span = createSpan(forge, sessionSampleRate = fakeSessionRate)

        val expectedRate = (fakeTraceRate * fakeSessionRate / 100f).coerceAtMost(100f)

        // When
        val result = testedSampler.getSampleRate(span)

        // Then
        assertThat(result).isCloseTo(expectedRate, Offset.offset(0.001f))
    }

    @Test
    fun `M return raw rate W getSampleRate(span) { session tag absent }`(
        @FloatForgery(min = 0f, max = 100f) fakeTraceRate: Float,
        forge: Forge
    ) {
        // Given
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)
        val span = createSpan(forge, sessionSampleRate = null)

        // When
        val result = testedSampler.getSampleRate(span)

        // Then
        assertThat(result).isEqualTo(fakeTraceRate)
    }

    @Test
    fun `M return raw rate W getSampleRate(span) { session tag is 100 }`(
        @FloatForgery(min = 0f, max = 100f) fakeTraceRate: Float,
        forge: Forge
    ) {
        // Given
        val delegate = DeterministicTraceSampler(fakeTraceRate)
        val testedSampler = SessionRebasedSampler(delegate)
        val span = createSpan(forge, sessionSampleRate = 100f)

        // When
        val result = testedSampler.getSampleRate(span)

        // Then
        assertThat(result).isCloseTo(fakeTraceRate, Offset.offset(0.001f))
    }

    @Test
    fun `M return raw rate W getSampleRate(span) { custom sampler delegate }`(
        forge: Forge
    ) {
        // Given
        val fakeRawRate = forge.aFloat(min = 0f, max = 100f)
        val mockDelegate = mock<Sampler<DatadogSpan>>()
        whenever(mockDelegate.getSampleRate()).thenReturn(fakeRawRate)
        val testedSampler = SessionRebasedSampler(mockDelegate)
        val span = createSpan(forge, sessionSampleRate = 50f)

        // When
        val result = testedSampler.getSampleRate(span)

        // Then
        assertThat(result).isEqualTo(fakeRawRate)
    }

    @Test
    fun `M return delegate per-span rate W getSampleRate(span) { delegate is SpanAwareSampler }`(
        forge: Forge
    ) {
        // Given
        val fakeStaticRate = forge.aFloat(min = 0f, max = 50f)
        val fakePerSpanRate = forge.aFloat(min = 50.01f, max = 100f)
        val stubDelegate = mock<SpanAwareDelegateSampler>()
        whenever(stubDelegate.getSampleRate()).thenReturn(fakeStaticRate)
        whenever(stubDelegate.getSampleRate(org.mockito.kotlin.any())).thenReturn(fakePerSpanRate)
        val testedSampler = SessionRebasedSampler(stubDelegate)
        val span = createSpan(forge, sessionSampleRate = 50f)

        // When
        val result = testedSampler.getSampleRate(span)

        // Then
        assertThat(result).isEqualTo(fakePerSpanRate)
    }

    // endregion

    // region helpers

    internal interface SpanAwareDelegateSampler : Sampler<DatadogSpan>, SpanAwareSampler

    private fun createSpan(forge: Forge, sessionSampleRate: Float?): DatadogSpan {
        val tags = buildMap<String, Any> {
            if (sessionSampleRate != null) {
                put(LogAttributes.RUM_SESSION_SAMPLE_RATE, sessionSampleRate)
            }
        }
        val traceId = mock<DatadogTraceId> {
            on { toLong() } doReturn forge.aLong()
        }
        val context = mock<DatadogSpanContext> {
            on { this.traceId } doReturn traceId
            on { this.tags } doReturn tags
        }
        return mock {
            on { context() } doReturn context
        }
    }

    private fun createSpans(
        forge: Forge,
        sessionSampleRate: Float?,
        count: Int = forge.anInt(min = 256, max = 1024)
    ): List<DatadogSpan> {
        return forge.aList(count) {
            createSpan(forge, sessionSampleRate)
        }
    }

    // endregion
}
