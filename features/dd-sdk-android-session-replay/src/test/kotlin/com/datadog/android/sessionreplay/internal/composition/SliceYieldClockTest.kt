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
internal class SliceYieldClockTest {

    @Test
    fun `M return false W shouldYield { slice just started }`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeSliceBudgetNs: Long
    ) {
        // Given
        val fakeClock = SequencedClock(fakeStartedAtNs)
        val testedClock = SliceYieldClock(fakeStartedAtNs, fakeClock, fakeSliceBudgetNs)

        // Then
        assertThat(testedClock.shouldYield()).isFalse()
    }

    @Test
    fun `M return true W shouldYield { slice budget elapsed }`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeSliceBudgetNs: Long
    ) {
        // Given
        val fakeClock = SequencedClock(fakeStartedAtNs + fakeSliceBudgetNs)
        val testedClock = SliceYieldClock(fakeStartedAtNs, fakeClock, fakeSliceBudgetNs)

        // Then
        assertThat(testedClock.shouldYield()).isTrue()
    }

    @Test
    fun `M reset the yield clock W markStart`(
        @LongForgery(min = 0L, max = 1_000_000L) fakeStartedAtNs: Long,
        @LongForgery(min = 1L, max = 1_000_000L) fakeSliceBudgetNs: Long
    ) {
        // Given
        val fakeClock = SequencedClock(
            fakeStartedAtNs + fakeSliceBudgetNs, // read by shouldYield() before marking
            fakeStartedAtNs + fakeSliceBudgetNs, // read by markStart() itself
            fakeStartedAtNs + fakeSliceBudgetNs // read by shouldYield() right after marking
        )
        val testedClock = SliceYieldClock(fakeStartedAtNs, fakeClock, fakeSliceBudgetNs)

        // When
        val beforeMark = testedClock.shouldYield()
        testedClock.markStart()
        val afterMark = testedClock.shouldYield()

        // Then
        assertThat(beforeMark).isTrue()
        assertThat(afterMark).isFalse()
    }

    private class SequencedClock(private vararg val readings: Long) : CaptureTimeProvider {
        private var index = 0

        override fun elapsedRealtimeNanos(): Long {
            val reading = readings[index.coerceAtMost(readings.size - 1)]
            if (index < readings.size - 1) index++
            return reading
        }
    }
}
