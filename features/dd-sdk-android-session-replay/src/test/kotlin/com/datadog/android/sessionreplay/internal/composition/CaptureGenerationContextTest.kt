/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CaptureGenerationContextTest {

    @Test
    fun `M cancel work W track { generation already expired }`(
        @LongForgery(min = 1L, max = 1_000L) fakeGenerationId: Long,
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeBudgetNs: Long
    ) {
        // Given
        val fakeClock = SequencedClock(fakeStartedAtNs)
        val testedContext = CaptureGenerationContext(
            id = fakeGenerationId,
            startedAtNs = fakeStartedAtNs,
            deadlineNs = fakeStartedAtNs + fakeBudgetNs,
            timeProvider = fakeClock
        )
        testedContext.expire()
        val fakeWork = RecordingCancellableCaptureWork()

        // When
        testedContext.track(fakeWork)

        // Then
        assertThat(fakeWork.cancelCount).isEqualTo(1)
    }

    @Test
    fun `M cancel work W track { generation expires between registration and recheck }`(
        @LongForgery(min = 1L, max = 1_000L) fakeGenerationId: Long,
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeBudgetNs: Long
    ) {
        // Given
        // The first isActive() check inside track() reads a timestamp before the deadline; the
        // second reads one at the deadline, so the generation transitions from active to expired
        // strictly between registering the work and re-checking liveness - the same window a
        // concurrent expire()/tryAccept() races with registration in production.
        val fakeDeadlineNs = fakeStartedAtNs + fakeBudgetNs
        val fakeClock = SequencedClock(fakeStartedAtNs, fakeDeadlineNs)
        val testedContext = CaptureGenerationContext(
            id = fakeGenerationId,
            startedAtNs = fakeStartedAtNs,
            deadlineNs = fakeDeadlineNs,
            timeProvider = fakeClock
        )
        val fakeWork = RecordingCancellableCaptureWork()

        // When
        testedContext.track(fakeWork)

        // Then
        assertThat(fakeWork.cancelCount).isGreaterThanOrEqualTo(1)
        assertThat(testedContext.isActive()).isFalse()
    }

    @Test
    fun `M not cancel work W track { generation remains active }`(
        @LongForgery(min = 1L, max = 1_000L) fakeGenerationId: Long,
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeBudgetNs: Long
    ) {
        // Given
        val fakeClock = SequencedClock(fakeStartedAtNs)
        val testedContext = CaptureGenerationContext(
            id = fakeGenerationId,
            startedAtNs = fakeStartedAtNs,
            deadlineNs = fakeStartedAtNs + fakeBudgetNs,
            timeProvider = fakeClock
        )
        val fakeWork = RecordingCancellableCaptureWork()

        // When
        testedContext.track(fakeWork)

        // Then
        assertThat(fakeWork.cancelCount).isZero()
    }

    @Test
    fun `M return null W createWorkToken { generation expires between admission and recheck }`(
        @LongForgery(min = 1L, max = 1_000L) fakeGenerationId: Long,
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeBudgetNs: Long
    ) {
        // Given
        // The first isActive() check inside createWorkToken() reads a timestamp before the
        // deadline (admitting the token); the second reads one at the deadline, so the generation
        // expires strictly between allocating the token and re-checking liveness - the same window
        // a concurrent expire()/tryAccept() races with allocation in production.
        val fakeDeadlineNs = fakeStartedAtNs + fakeBudgetNs
        val fakeClock = SequencedClock(fakeStartedAtNs, fakeDeadlineNs)
        val testedContext = CaptureGenerationContext(
            id = fakeGenerationId,
            startedAtNs = fakeStartedAtNs,
            deadlineNs = fakeDeadlineNs,
            timeProvider = fakeClock
        )

        // When
        val token = testedContext.createWorkToken()

        // Then
        assertThat(token).isNull()
        assertThat(testedContext.isActive()).isFalse()
    }

    @Test
    fun `M reject the second call W complete { token already completed }`(
        @LongForgery(min = 1L, max = 1_000L) fakeGenerationId: Long,
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeBudgetNs: Long
    ) {
        // Given
        val fakeClock = SequencedClock(fakeStartedAtNs)
        val testedContext = CaptureGenerationContext(
            id = fakeGenerationId,
            startedAtNs = fakeStartedAtNs,
            deadlineNs = fakeStartedAtNs + fakeBudgetNs,
            timeProvider = fakeClock
        )
        val token = testedContext.createWorkToken()

        // When
        val firstComplete = token?.complete()
        val secondComplete = token?.complete()

        // Then
        assertThat(firstComplete).isTrue()
        assertThat(secondComplete).isFalse()
    }

    private class SequencedClock(private vararg val readings: Long) : CaptureTimeProvider {
        private var index = 0

        override fun elapsedRealtimeNanos(): Long {
            val reading = readings[index.coerceAtMost(readings.size - 1)]
            if (index < readings.size - 1) index++
            return reading
        }
    }

    private class RecordingCancellableCaptureWork : CancellableCaptureWork {
        var cancelCount = 0
            private set

        override fun cancel() {
            cancelCount++
        }
    }
}
