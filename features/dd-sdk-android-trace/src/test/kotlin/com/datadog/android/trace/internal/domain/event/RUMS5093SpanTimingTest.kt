/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.core.DDSpan
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

/**
 * Reproduction tests for RUMS-5093 Issue 2: Incorrect timing of the android.request span.
 *
 * The [CoreTracerSpanToSpanEventMapper] applies [DatadogContext.time.serverTimeOffsetNs] to the
 * span start time:
 *
 *   SpanEvent.start = model.startTime + serverTimeOffsetNs
 *
 * When the device clock diverges significantly from the Datadog NTP reference clock
 * (e.g. the device is 30 seconds ahead of the server), the correction shifts the android.request
 * span's absolute start timestamp in the wrong direction relative to backend spans that are
 * timestamped by the Datadog Agent using the server's wall clock.
 *
 * The result is that the android.request span appears to start/end at a completely different time
 * from backend spans in the waterfall, or appears to have no duration, even though the underlying
 * RUM resource duration (computed from monotonic nanoTime) is accurate.
 *
 * The tests below document the expected (correct) behaviour and show that a large device clock skew
 * produces a span start time that is displaced by exactly serverTimeOffsetNs — which can push the
 * android.request span outside the time window of backend spans.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RUMS5093SpanTimingTest {

    private lateinit var testedMapper: CoreTracerSpanToSpanEventMapper

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    @BeforeEach
    fun `set up`() {
        testedMapper = CoreTracerSpanToSpanEventMapper(fakeNetworkInfoEnabled)
    }

    /**
     * FAILING TEST — proves Issue 2 of RUMS-5093 (NTP offset displaces the span start time).
     *
     * When the device clock is 30 seconds AHEAD of the Datadog NTP server, serverTimeOffsetNs is
     * a large NEGATIVE value (approx -30_000_000_000 ns).
     *
     * Applying this correction to the span start time shifts it 30 seconds into the past relative
     * to the device's wall clock. Backend spans are timestamped by the Datadog Agent using the
     * Agent's (server-side) wall clock and are NOT corrected. The android.request span therefore
     * appears to start 30 seconds BEFORE the backend spans in the APM waterfall, even though the
     * request was actually in flight for less than a second.
     *
     * The correct behaviour: the span start time after NTP correction must be within a reasonable
     * tolerance of the raw span start time (i.e. within ±5 seconds). If the device clock is more
     * than 5 seconds off, the user experience is degraded.
     *
     * This test documents the existing (buggy) behaviour: it verifies that with a 30-second clock
     * skew, the corrected start time differs from the raw start time by the FULL offset —
     * demonstrating the displacement. A conformant fix would cap or smooth the offset so the
     * displacement stays within an acceptable bound.
     *
     * The assertion below uses `isNotEqualTo` (i.e. proves the displacement exists) and then
     * asserts a tighter bound that SHOULD hold after a fix — which currently FAILS.
     */
    @Test
    fun `M produce start time close to backend spans W map() {device clock 30s ahead of NTP server}`(
        @Forgery fakeSpan: DDSpan,
        @Forgery fakeContext: DatadogContext,
        @LongForgery(min = 1_000_000_000L, max = 10_000_000_000L) spanStartTimeNs: Long
    ) {
        // Given — device clock is 30 seconds ahead of the NTP reference
        val deviceAheadOfServerNs = TimeUnit.SECONDS.toNanos(30)          // +30s device offset
        val serverTimeOffsetNs = -deviceAheadOfServerNs                   // correction is negative

        whenever(fakeSpan.startTime).thenReturn(spanStartTimeNs)

        val contextWithLargeSkew = fakeContext.copy(
            time = TimeInfo(
                deviceTimeNs = spanStartTimeNs,
                serverTimeNs = spanStartTimeNs + serverTimeOffsetNs,
                serverTimeOffsetNs = serverTimeOffsetNs,
                serverTimeOffsetMs = serverTimeOffsetNs / 1_000_000L
            )
        )

        // When
        val spanEvent = testedMapper.map(contextWithLargeSkew, fakeSpan)

        // Then
        val correctedStart = spanEvent.start
        val rawStart = spanStartTimeNs

        // Document the displacement: the corrected start is shifted by the full NTP offset
        assertThat(correctedStart)
            .withFailMessage(
                "RUMS-5093 Issue 2: The span start time should be shifted by serverTimeOffsetNs=%d ns. " +
                    "Expected correctedStart=%d = rawStart=%d + offset=%d",
                serverTimeOffsetNs,
                correctedStart,
                rawStart,
                serverTimeOffsetNs
            )
            .isEqualTo(rawStart + serverTimeOffsetNs)

        // FAILING ASSERTION: After a fix, the corrected start time must be within ±5 seconds of
        // the raw start time (the fix should detect excessive clock skew and avoid applying it).
        // This assertion currently FAILS because the code applies the full 30-second offset.
        val maxAcceptableDisplacementNs = TimeUnit.SECONDS.toNanos(5)     // ±5s tolerance
        val actualDisplacementNs = Math.abs(correctedStart - rawStart)

        assertThat(actualDisplacementNs)
            .withFailMessage(
                "RUMS-5093 Issue 2: The android.request span start time is displaced by %d ns " +
                    "(%d seconds) relative to raw start time. This is caused by applying a large " +
                    "serverTimeOffsetNs=%d ns (device 30s ahead of NTP server). " +
                    "Backend spans are NOT offset-corrected, so the android.request span will " +
                    "appear 30 seconds before backend spans in the APM waterfall. " +
                    "The displacement must be less than %d ns (5 seconds) for correct trace display.",
                actualDisplacementNs,
                TimeUnit.NANOSECONDS.toSeconds(actualDisplacementNs),
                serverTimeOffsetNs,
                maxAcceptableDisplacementNs
            )
            .isLessThan(maxAcceptableDisplacementNs)
    }

    /**
     * PASSING TEST — documents that the NTP offset IS applied to the span start time.
     *
     * This test verifies the current code behaviour: the corrected start equals startTime + offset.
     * It passes to show we understand the mechanism. The bug is that this mechanism causes
     * misalignment when the device clock skew is large.
     */
    @Test
    fun `M apply NTP server offset to span start time W map() {serverTimeOffsetNs non-zero}`(
        @Forgery fakeSpan: DDSpan,
        @Forgery fakeContext: DatadogContext,
        @LongForgery(min = 1_000_000_000L, max = 10_000_000_000L) spanStartTimeNs: Long,
        @LongForgery serverTimeOffsetNs: Long
    ) {
        // Given
        whenever(fakeSpan.startTime).thenReturn(spanStartTimeNs)

        val contextWithOffset = fakeContext.copy(
            time = TimeInfo(
                deviceTimeNs = spanStartTimeNs,
                serverTimeNs = spanStartTimeNs + serverTimeOffsetNs,
                serverTimeOffsetNs = serverTimeOffsetNs,
                serverTimeOffsetMs = serverTimeOffsetNs / 1_000_000L
            )
        )

        // When
        val spanEvent = testedMapper.map(contextWithOffset, fakeSpan)

        // Then — current behaviour: start = rawStart + serverTimeOffsetNs
        assertThat(spanEvent.start)
            .isEqualTo(spanStartTimeNs + serverTimeOffsetNs)
    }
}
