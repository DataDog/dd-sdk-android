/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.vitals.CpuStatReader
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CpuDatapointReaderTest {

    // Min is 2_500 so that intervalMs * MAX_GAP_FACTOR always covers the largest fakeElapsedMs the
    // tests below forge (5_000 ms). Below that, CpuDatapointReader's "gap too large" guard
    // (elapsedMs > intervalMs * MAX_GAP_FACTOR) discards the sample and read() returns null,
    // which made every checkNotNull(result) test fail on unlucky seeds.
    @LongForgery(min = 2_500L, max = 60_000L)
    var fakeIntervalMs: Long = 0L

    @LongForgery(min = 1_000L, max = 1_000_000L)
    var fakeStartTimestampMs: Long = 0L

    @IntForgery(min = 1, max = 10_000)
    var fakeBaselineTicks: Int = 0

    val mockCpuStatReader: CpuStatReader = mock()

    val mockTimeProvider: TimeProvider = mock<TimeProvider> {
        on { getServerTimestampMillis() } doAnswer { fakeStartTimestampMs }
        on { getDeviceElapsedTimeNanos() } doAnswer { fakeStartTimestampMs * NS_PER_MS }
    }

    private fun buildReader(availableProcessors: Int = 1) = CpuDatapointReader(
        cpuStatReader = mockCpuStatReader,
        timeProvider = mockTimeProvider,
        intervalMs = fakeIntervalMs,
        availableProcessors = availableProcessors
    )

    /**
     * Primes the reader with [fakeBaselineTicks] (returns null — no baseline yet), advances the
     * clock by [elapsedMs], then returns the second sample built from [secondTicks] (or `null` to
     * simulate the stat read failing between samples). Elapsed time is stubbed as a sequence so
     * the prime read sees the start instant and the second read sees start + [elapsedMs].
     */
    private fun readSecondSample(
        elapsedMs: Long,
        secondTicks: Double?,
        availableProcessors: Int = 1
    ): DataPoint<Double>? {
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos()).doReturn(
            fakeStartTimestampMs * NS_PER_MS,
            (fakeStartTimestampMs + elapsedMs) * NS_PER_MS
        )
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn (fakeStartTimestampMs + elapsedMs)
        whenever(mockCpuStatReader.readActiveTime())
            .doReturnConsecutively(listOf(fakeBaselineTicks.toDouble(), secondTicks))
        return buildReader(availableProcessors).run {
            read()
            read()
        }
    }

    @Test
    fun `M return null W read() {first sample}`() {
        // Given
        whenever(mockCpuStatReader.readActiveTime()) doReturn fakeBaselineTicks.toDouble()

        // When
        val result = buildReader().read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() {stat read returns null}`() {
        // Given
        whenever(mockCpuStatReader.readActiveTime()) doReturn null

        // When
        val result = buildReader().read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() {stat read fails on second sample}`(
        @LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long
    ) {
        // When
        val result = readSecondSample(elapsedMs = fakeElapsedMs, secondTicks = null)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return 0 W read() {zero elapsed and no tick delta}`() {
        // When — both samples at the same nano instant with unchanged ticks.
        // Without the elapsedMs floor this would be 0.0 / 0.0 = NaN (NaN survives coerceIn).
        val result = readSecondSample(elapsedMs = 0L, secondTicks = fakeBaselineTicks.toDouble())

        // Then — the 1 ms floor keeps the result finite
        checkNotNull(result)
        assertThat(result.value).isEqualTo(0.0)
    }

    @Test
    fun `M return cpu percent W read() {second sample}`(
        @DoubleForgery(min = 1.0, max = 80.0) fakeCpuPercent: Double,
        @LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long
    ) {
        // Given
        val deltaTicks = (fakeCpuPercent * fakeElapsedMs / 1000.0).toInt().coerceAtLeast(2)

        // When
        val result = readSecondSample(
            elapsedMs = fakeElapsedMs,
            secondTicks = (fakeBaselineTicks + deltaTicks).toDouble()
        )

        // Then
        checkNotNull(result)
        val expectedPercent = deltaTicks * 1000.0 / fakeElapsedMs
        assertThat(result.value).isCloseTo(expectedPercent, Offset.offset(0.1))
        assertThat(result.timestampNs).isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeStartTimestampMs + fakeElapsedMs))
    }

    @Test
    fun `M return 0 W read() {ticks unchanged}`(@LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long) {
        // When
        val result = readSecondSample(elapsedMs = fakeElapsedMs, secondTicks = fakeBaselineTicks.toDouble())

        // Then
        checkNotNull(result)
        assertThat(result.value).isEqualTo(0.0)
    }

    @Test
    fun `M clamp to 100 W read() {spike exceeds total capacity}`(
        @LongForgery(min = 500L, max = 2000L) fakeElapsedMs: Long
    ) {
        // Given — 200 ticks/s on one core with availableProcessors=1 → 200% → clamped to 100
        val deltaTicks = (200.0 * fakeElapsedMs / 1000.0).toInt()

        // When
        val result = readSecondSample(
            elapsedMs = fakeElapsedMs,
            secondTicks = (fakeBaselineTicks + deltaTicks).toDouble()
        )

        // Then
        checkNotNull(result)
        assertThat(result.value).isEqualTo(100.0)
    }

    @Test
    fun `M normalise by core count W read() {multi-core usage}`(
        @IntForgery(min = 2, max = 8) fakeCoreCount: Int,
        @LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long,
        @DoubleForgery(min = 1.0, max = 50.0) fakePerCorePct: Double
    ) {
        // Given — deltaTicks = fakePerCorePct% on ONE core → fakePerCorePct/fakeCoreCount % of total
        val deltaTicks = (fakePerCorePct * fakeElapsedMs / 1000.0).toInt().coerceAtLeast(1)

        // When
        val result = readSecondSample(
            elapsedMs = fakeElapsedMs,
            availableProcessors = fakeCoreCount,
            secondTicks = (fakeBaselineTicks + deltaTicks).toDouble()
        )

        // Then
        checkNotNull(result)
        val expectedPercent = (deltaTicks * 1000.0 / fakeElapsedMs) / fakeCoreCount
        assertThat(result.value).isCloseTo(expectedPercent, Offset.offset(0.1))
    }

    private companion object {
        private const val NS_PER_MS = 1_000_000L
    }
}
