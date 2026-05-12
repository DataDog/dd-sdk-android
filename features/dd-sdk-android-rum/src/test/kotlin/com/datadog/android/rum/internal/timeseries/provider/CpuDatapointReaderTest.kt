/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CpuDatapointReaderTest {

    private lateinit var testedReader: CpuDatapointReader

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakeStatFile: File

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @LongForgery(min = 100L, max = 60_000L)
    var fakeIntervalMs: Long = 0L

    @LongForgery(min = 1_000L, max = 1_000_000L)
    var fakeStartTimestampMs: Long = 0L

    // /proc/self/stat fields (indices 0–12 before utime at index 13)
    @IntForgery(1)
    var fakePid: Int = 0

    @StringForgery(regex = "\\(\\w+\\)")
    lateinit var fakeCommand: String

    @StringForgery(regex = "[RSDZTtWXxKWP]")
    lateinit var fakeState: String

    @IntForgery(min = 1)
    var fakePpid: Int = 0

    @IntForgery(min = 1)
    var fakePgrp: Int = 0

    @IntForgery(min = 1)
    var fakeSession: Int = 0

    @IntForgery(min = 1)
    var fakeTtyNr: Int = 0

    @IntForgery(min = 1)
    var fakeTpgid: Int = 0

    @IntForgery(min = 1)
    var fakeFlags: Int = 0

    @IntForgery(min = 1)
    var fakeMinFlt: Int = 0

    @IntForgery(min = 1)
    var fakeCMinFlt: Int = 0

    @IntForgery(min = 1)
    var fakeMajFlt: Int = 0

    @IntForgery(min = 1)
    var fakeCMajFlt: Int = 0

    @IntForgery(min = 1, max = 10_000)
    var fakeUtime: Int = 0

    @IntForgery(min = 1, max = 10_000)
    var fakeStime: Int = 0

    @BeforeEach
    fun `set up`() {
        fakeStatFile = File(tempDir, "stat")
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeStartTimestampMs
        testedReader = CpuDatapointReader(
            statFile = fakeStatFile,
            cpuTimeProvider = mockTimeProvider,
            intervalMs = fakeIntervalMs,
            internalLogger = mock()
        )
    }

    @Test
    fun `M use default stat file W init()`() {
        // When
        val reader = CpuDatapointReader(
            cpuTimeProvider = mockTimeProvider,
            intervalMs = fakeIntervalMs,
            internalLogger = mock()
        )

        // Then
        assertThat(reader.statFile).isEqualTo(CpuDatapointReader.STAT_FILE)
    }

    @Test
    fun `M return null W read() {first sample}`() {
        // Given
        fakeStatFile.writeText(generateStatContent(fakeUtime))

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return cpu percent W read() {second sample}`(
        @DoubleForgery(min = 1.0, max = 80.0) fakeCpuPercent: Double,
        @LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long
    ) {
        // Given — seed reference point (utime + stime together form cpuTicks)
        fakeStatFile.writeText(generateStatContent(fakeUtime))
        testedReader.read()

        // deltaTicks drives the percent: percent = deltaTicks * 1000 / elapsedMs (CLK_TCK=100)
        val deltaTicks = (fakeCpuPercent * fakeElapsedMs / 1000.0).toInt().coerceAtLeast(1)
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeStartTimestampMs + fakeElapsedMs)
        // Distribute delta arbitrarily across utime and stime
        val deltaUtime = deltaTicks / 2
        val deltaStime = deltaTicks - deltaUtime
        fakeStatFile.writeText(generateStatContent(fakeUtime + deltaUtime, fakeStime + deltaStime))

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNotNull
        val expectedPercent = deltaTicks * 1000.0 / fakeElapsedMs
        assertThat(result!!.value).isCloseTo(expectedPercent, Offset.offset(0.1))
        assertThat(result.timestampNs).isEqualTo(
            TimeUnit.MILLISECONDS.toNanos(fakeStartTimestampMs + fakeElapsedMs)
        )
    }

    @Test
    fun `M return 0 W read() {utime unchanged}`(@LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long) {
        // Given
        fakeStatFile.writeText(generateStatContent(fakeUtime))
        testedReader.read()

        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeStartTimestampMs + fakeElapsedMs)

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.value).isEqualTo(0.0)
    }

    @Test
    fun `M clamp to 100 W read() {multi-core spike}`(@LongForgery(min = 500L, max = 2000L) fakeElapsedMs: Long) {
        // Given — seed reference point
        fakeStatFile.writeText(generateStatContent(fakeUtime))
        testedReader.read()

        // 200 ticks/s over the interval → 200% on one core → clamped to 100
        val deltaUtime = (200.0 * fakeElapsedMs / 1000.0).toInt()
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeStartTimestampMs + fakeElapsedMs)
        fakeStatFile.writeText(generateStatContent(fakeUtime + deltaUtime))

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.value).isEqualTo(100.0)
    }

    @Test
    fun `M use only utime W read() {stime field absent}`(
        @LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long,
        @IntForgery(min = 1, max = 500) fakeDeltaUtime: Int
    ) {
        // Given — content with exactly 14 tokens (utime at [13], no stime at [14])
        fakeStatFile.writeText(generateStatContent(fakeUtime, includeStime = false))
        testedReader.read()

        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeStartTimestampMs + fakeElapsedMs)
        fakeStatFile.writeText(generateStatContent(fakeUtime + fakeDeltaUtime, includeStime = false))

        // When
        val result = testedReader.read()

        // Then
        val expectedPercent = fakeDeltaUtime * 1000.0 / fakeElapsedMs
        assertThat(result).isNotNull
        assertThat(result!!.value).isCloseTo(expectedPercent.coerceAtMost(100.0), Offset.offset(0.1))
    }

    @Test
    fun `M return null W read() {stat file missing}`() {
        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() {stat file has invalid content}`(@StringForgery fakeContent: String) {
        // Given
        fakeStatFile.writeText(fakeContent)

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() {stat file missing on second sample}`(
        @LongForgery(min = 500L, max = 5000L) fakeElapsedMs: Long
    ) {
        // Given — seed reference point
        fakeStatFile.writeText(generateStatContent(fakeUtime))
        testedReader.read()

        fakeStatFile.delete()
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn (fakeStartTimestampMs + fakeElapsedMs)

        // When
        val result = testedReader.read()

        // Then
        assertThat(result).isNull()
    }

    private fun generateStatContent(utime: Int, stime: Int = fakeStime, includeStime: Boolean = true): String {
        val fields = mutableListOf<Any>(
            fakePid, fakeCommand, fakeState, fakePpid, fakePgrp,
            fakeSession, fakeTtyNr, fakeTpgid, fakeFlags,
            fakeMinFlt, fakeCMinFlt, fakeMajFlt, fakeCMajFlt,
            utime
        )
        if (includeStime) fields.add(stime)
        return fields.joinToString(" ")
    }
}
