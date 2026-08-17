/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CaptureGenerationContextTest {

    @Test
    fun `M cancel work W track { generation already expired }`() {
        // Given
        val clock = SequencedClock(0L)
        val context = CaptureGenerationContext(1L, 0L, deadlineNs = 100L, timeProvider = clock)
        context.expire()
        val work = RecordingCancellableCaptureWork()

        // When
        context.track(work)

        // Then
        assertThat(work.cancelCount).isEqualTo(1)
    }

    @Test
    fun `M cancel work W track { generation expires between registration and recheck }`() {
        // Given
        // First isActive() check (inside track()) reads a timestamp before the deadline; the second
        // reads one at/after it, so the generation transitions from active to expired strictly
        // between registering the work and re-checking liveness - the same place a concurrent
        // expire()/tryAccept() could otherwise race with registration in production.
        val clock = SequencedClock(0L, 100L)
        val context = CaptureGenerationContext(1L, 0L, deadlineNs = 100L, timeProvider = clock)
        val work = RecordingCancellableCaptureWork()

        // When
        context.track(work)

        // Then
        assertThat(work.cancelCount).isGreaterThanOrEqualTo(1)
        assertThat(context.isActive()).isFalse()
    }

    @Test
    fun `M not cancel work W track { generation remains active }`() {
        // Given
        val clock = SequencedClock(0L)
        val context = CaptureGenerationContext(1L, 0L, deadlineNs = 100L, timeProvider = clock)
        val work = RecordingCancellableCaptureWork()

        // When
        context.track(work)

        // Then
        assertThat(work.cancelCount).isZero()
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
