/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.log.LogAttributes
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DeterministicTraceSamplerTest {

    private lateinit var testedSampler: DeterministicTraceSampler

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
        testedSampler = DeterministicTraceSampler(sampleRateProvider = { fakeSampleRate })
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

    // region Session rate rebasing

    @Test
    fun `M drop all spans W sample() {session sample rate is 50 and rebased rate is 0}`(forge: Forge) {
        // Given: traceSampleRate=0, sessionSampleRate=50 → rebased = 0*50/100 = 0
        val fakeTraceSampleRate = 0f
        val fakeSessionSampleRate = 50f
        testedSampler = DeterministicTraceSampler(fakeTraceSampleRate) { fakeSessionSampleRate }
        val spansWithSession = createSpansWithSessionContext(forge)

        var sampledIn = 0

        // When
        spansWithSession.forEach { if (testedSampler.sample(it)) sampledIn++ }

        // Then — none must be sampled (rebased rate = 0)
        assertThat(sampledIn).isEqualTo(0)
    }

    @Test
    fun `M apply rebased rate W sample() {session sample rate is 50 - deterministic}`() {
        // Given: traceSampleRate=50, sessionSampleRate=50 → rebasedRate = 50*50/100 = 25%
        //
        // Knuth hash: hash = lastHexSegment * SAMPLER_HASHER
        //   n=1 → hash = 1_111_111_111_111_111_111
        //   n=5 → hash = 5_555_555_555_555_555_555
        //
        // threshold(25%) = MAX_ULONG * 25/100 ≈ 4_611_686_018_427_387_903
        // threshold(50%) = MAX_ULONG * 50/100 ≈ 9_223_372_036_854_775_807
        //
        // n=1: hash(1.1T) < threshold(25%)(4.6T)  → SAMPLED at 25%
        // n=5: hash(5.5T) > threshold(25%)(4.6T)  → NOT SAMPLED at 25%  ← rebasing dropped it
        //      hash(5.5T) < threshold(50%)(9.2T)  → would be SAMPLED at raw 50%  ← proves rebasing had effect
        val traceSampleRate = 50f
        val sessionSampleRate = 50f
        testedSampler = DeterministicTraceSampler(traceSampleRate) { sessionSampleRate }

        val spanSampled = createSpanWithFixedSessionId("aaaaaaaa-bbbb-cccc-dddd-000000000001")
        val spanDropped = createSpanWithFixedSessionId("aaaaaaaa-bbbb-cccc-dddd-000000000005")

        // When + Then
        assertThat(testedSampler.sample(spanSampled)).isTrue() // hash 1.1T < rebased threshold 4.6T
        assertThat(testedSampler.sample(spanDropped)).isFalse() // hash 5.5T > rebased threshold 4.6T
    }

    @Test
    fun `M apply rebased rate W sample() {session sample rate provider is below 100}`() {
        // Given: global rebasing uses provider session sample rate regardless of span session tags
        // traceSampleRate=50, sessionSampleRate=50 -> effective=25
        // n=5 -> hash=5.5T > threshold(25%) 4.6T -> NOT SAMPLED
        val traceSampleRate = 50f
        val sessionSampleRate = 50f
        testedSampler = DeterministicTraceSampler(traceSampleRate) { sessionSampleRate }

        val spanWithoutSession = createSpanWithFixedTraceId(5L)

        // When + Then
        assertThat(testedSampler.sample(spanWithoutSession)).isFalse()
    }

    @Test
    fun `M use raw trace rate W sample() {session sample rate is 100}`() {
        // Given: sessionSampleRate=100 → rebased = traceSampleRate*100/100 = traceSampleRate (50%)
        // threshold(50%) = MAX_ULONG * 50/100 ≈ 9_223_372_036_854_775_807
        //
        // n=5: hash(5.5T) < threshold(50%)(9.2T) → SAMPLED (same as raw 50%)
        // n=9: hash(9.9T) > threshold(50%)(9.2T) → NOT SAMPLED (same as raw 50%)
        val traceSampleRate = 50f
        val sessionSampleRate = 100f
        testedSampler = DeterministicTraceSampler(traceSampleRate) { sessionSampleRate }

        val spanSampled = createSpanWithFixedSessionId(
            sessionId = "aaaaaaaa-bbbb-cccc-dddd-000000000005",
            traceIdValue = 5L
        )
        val spanDropped = createSpanWithFixedSessionId(
            sessionId = "aaaaaaaa-bbbb-cccc-dddd-000000000009",
            traceIdValue = 9L
        )

        // When + Then — rebased rate == raw trace rate, identical sampling decisions
        assertThat(testedSampler.sample(spanSampled)).isTrue()
        assertThat(testedSampler.sample(spanDropped)).isFalse()
    }

    @Test
    fun `M drop all spans W sample() {session sample rate is 0}`(forge: Forge) {
        // Given: sessionSampleRate=0 → rebased = traceSampleRate*0/100 = 0
        val fakeTraceSampleRate = 50f
        val fakeSessionSampleRate = 0f
        testedSampler = DeterministicTraceSampler(fakeTraceSampleRate) { fakeSessionSampleRate }
        val spansWithSession = createSpansWithSessionContext(forge)

        var sampledIn = 0

        // When
        spansWithSession.forEach { if (testedSampler.sample(it)) sampledIn++ }

        // Then — all spans dropped (effective rate = 0)
        assertThat(sampledIn).isEqualTo(0)
    }

    @Test
    fun `M use raw trace rate W sample() {sessionSampleRateProvider is default 100}`() {
        // Given: no sessionSampleRateProvider -> defaults to 100f -> getSampleRate() returns traceSampleRate
        // threshold(50%) = MAX_ULONG * 50/100 ≈ 9_223_372_036_854_775_807
        //
        // n=5: hash(5.5T) < threshold(50%)(9.2T) → SAMPLED
        // n=9: hash(9.9T) > threshold(50%)(9.2T) → NOT SAMPLED
        val traceSampleRate = 50f
        testedSampler = DeterministicTraceSampler(traceSampleRate) // no sessionSampleRateProvider → default 100f

        val spanSampled = createSpanWithFixedTraceId(5L)
        val spanDropped = createSpanWithFixedTraceId(9L)

        // When + Then — no provider → no rebasing → raw trace rate used
        assertThat(testedSampler.sample(spanSampled)).isTrue()
        assertThat(testedSampler.sample(spanDropped)).isFalse()
    }

    @Test
    fun `M use context session sample rate W getSampleRate(item) {no sessionSampleRateProvider configured}`(
        @FloatForgery(min = 0f, max = 100f) traceSampleRate: Float,
        @FloatForgery(min = 0f, max = 99f) sessionSampleRate: Float,
        @LongForgery fakeTraceIdLong: Long
    ) {
        // Given
        testedSampler = DeterministicTraceSampler(traceSampleRate)
        val expected = traceSampleRate * sessionSampleRate / 100f

        val traceId = mock<DatadogTraceId> {
            on { toLong() } doReturn fakeTraceIdLong
        }
        val context = mock<DatadogSpanContext> {
            on { this.traceId } doReturn traceId
            on { tags } doReturn mapOf(
                "session_sample_rate" to sessionSampleRate
            )
        }
        val span = mock<DatadogSpan> {
            on { context() } doReturn context
        }

        // When
        val result = testedSampler.getSampleRate(span)

        // Then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M use raw trace rate W getSampleRate(item) {session sample rate unavailable}`(
        @FloatForgery(min = 0f, max = 100f) traceSampleRate: Float,
        @LongForgery fakeTraceIdLong: Long
    ) {
        // Given: no provider rate and no session_sample_rate tag available
        testedSampler = DeterministicTraceSampler(traceSampleRate)

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
        val result = testedSampler.getSampleRate(span)

        // Then — no session rate available → raw trace rate
        assertThat(result).isEqualTo(traceSampleRate)
    }

    // endregion

    // region Helpers

    private fun createSpanWithFixedSessionId(
        sessionId: String? = null,
        traceIdValue: Long = 0L
    ): DatadogSpan {
        val traceId = mock<DatadogTraceId> { on { toLong() } doReturn traceIdValue }
        val tagsMap = buildMap<String, Any> {
            if (sessionId != null) put(LogAttributes.RUM_SESSION_ID, sessionId)
        }
        val context = mock<DatadogSpanContext> {
            on { this.traceId } doReturn traceId
            on { tags } doReturn tagsMap
        }
        return mock<DatadogSpan> { on { context() } doReturn context }
    }

    private fun createSpanWithFixedTraceId(traceIdLong: Long): DatadogSpan {
        val traceId = mock<DatadogTraceId> { on { toLong() } doReturn traceIdLong }
        val context = mock<DatadogSpanContext> {
            on { this.traceId } doReturn traceId
            on { tags } doReturn emptyMap()
        }
        return mock<DatadogSpan> { on { context() } doReturn context }
    }

    private fun createSpansWithSessionContext(forge: Forge): List<DatadogSpan> {
        val listSize = forge.anInt(256, 1024)
        return (0 until listSize).map {
            val fakeSessionId = UUID.randomUUID().toString()
            val traceId = mock<DatadogTraceId> { on { toLong() } doReturn forge.aLong() }
            val context = mock<DatadogSpanContext> {
                on { this.traceId } doReturn traceId
                on { tags } doReturn mapOf(LogAttributes.RUM_SESSION_ID to fakeSessionId)
            }
            mock<DatadogSpan> { on { context() } doReturn context }
        }
    }

    // endregion
}
