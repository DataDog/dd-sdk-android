/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.rum.model.RumTimeseriesCpuEvent
import com.datadog.android.rum.model.RumTimeseriesMemoryEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DeltaEncoderTest {

    @Test
    fun `M return null W encodeMemory() { single sample }`() {
        // Given
        val buffer = listOf(
            RumTimeseriesMemoryEvent.Data(
                timestamp = 1_000_000_000L,
                dataPoint = RumTimeseriesMemoryEvent.DataPoint(memoryMax = 100.0, memoryPercent = 10.0)
            )
        )

        // When
        val result = DeltaEncoder.encodeMemory(buffer)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W encodeMemory() { empty buffer }`() {
        // Given
        val buffer = emptyList<RumTimeseriesMemoryEvent.Data>()

        // When
        val result = DeltaEncoder.encodeMemory(buffer)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M encode correct deltas W encodeMemory() { 3 samples }`() {
        // Given
        val buffer = listOf(
            RumTimeseriesMemoryEvent.Data(
                timestamp = 1_000_000_000L,
                dataPoint = RumTimeseriesMemoryEvent.DataPoint(memoryMax = 100.0, memoryPercent = 10.0)
            ),
            RumTimeseriesMemoryEvent.Data(
                timestamp = 2_000_000_000L,
                dataPoint = RumTimeseriesMemoryEvent.DataPoint(memoryMax = 200.5, memoryPercent = 20.0)
            ),
            RumTimeseriesMemoryEvent.Data(
                timestamp = 3_000_000_000L,
                dataPoint = RumTimeseriesMemoryEvent.DataPoint(memoryMax = 200.5, memoryPercent = 20.5)
            )
        )

        // When
        val result = DeltaEncoder.encodeMemory(buffer)!!

        // Then
        assertThat(result.get("precision").asInt).isEqualTo(4)

        val ts = result.getAsJsonArray("ts")
        assertThat(ts[0].asLong).isEqualTo(1_000_000_000L)
        assertThat(ts[1].asLong).isEqualTo(1_000_000_000L)
        assertThat(ts[2].asLong).isEqualTo(1_000_000_000L)

        val memoryMax = result.getAsJsonArray("memory_max")
        // 100.0 * 10000 = 1_000_000
        assertThat(memoryMax[0].asLong).isEqualTo(1_000_000L)
        // (200.5 - 100.0) * 10000 = 1_005_000
        assertThat(memoryMax[1].asLong).isEqualTo(1_005_000L)
        // (200.5 - 200.5) * 10000 = 0
        assertThat(memoryMax[2].asLong).isEqualTo(0L)

        val memoryPercent = result.getAsJsonArray("memory_percent")
        // 10.0 * 10000 = 100_000
        assertThat(memoryPercent[0].asLong).isEqualTo(100_000L)
        // (20.0 - 10.0) * 10000 = 100_000
        assertThat(memoryPercent[1].asLong).isEqualTo(100_000L)
        // (20.5 - 20.0) * 10000 = 5_000
        assertThat(memoryPercent[2].asLong).isEqualTo(5_000L)
    }

    @Test
    fun `M encode correct deltas W encodeCpu() { 3 samples }`() {
        // Given
        val buffer = listOf(
            RumTimeseriesCpuEvent.Data(
                timestamp = 1_000_000_000L,
                dataPoint = RumTimeseriesCpuEvent.DataPoint(cpuUsage = 42.5)
            ),
            RumTimeseriesCpuEvent.Data(
                timestamp = 2_000_000_000L,
                dataPoint = RumTimeseriesCpuEvent.DataPoint(cpuUsage = 43.0)
            ),
            RumTimeseriesCpuEvent.Data(
                timestamp = 3_000_000_000L,
                dataPoint = RumTimeseriesCpuEvent.DataPoint(cpuUsage = 42.0)
            )
        )

        // When
        val result = DeltaEncoder.encodeCpu(buffer)!!

        // Then
        assertThat(result.get("precision").asInt).isEqualTo(4)

        val ts = result.getAsJsonArray("ts")
        assertThat(ts[0].asLong).isEqualTo(1_000_000_000L)
        assertThat(ts[1].asLong).isEqualTo(1_000_000_000L)
        assertThat(ts[2].asLong).isEqualTo(1_000_000_000L)

        val cpuUsage = result.getAsJsonArray("cpu_usage")
        // 42.5 * 10000 = 425_000
        assertThat(cpuUsage[0].asLong).isEqualTo(425_000L)
        // (43.0 - 42.5) * 10000 = 5_000
        assertThat(cpuUsage[1].asLong).isEqualTo(5_000L)
        // (42.0 - 43.0) * 10000 = -10_000
        assertThat(cpuUsage[2].asLong).isEqualTo(-10_000L)
    }

    @Test
    fun `M return null W encodeCpu() { single sample }`() {
        // Given
        val buffer = listOf(
            RumTimeseriesCpuEvent.Data(
                timestamp = 1_000_000_000L,
                dataPoint = RumTimeseriesCpuEvent.DataPoint(cpuUsage = 42.5)
            )
        )

        // When
        val result = DeltaEncoder.encodeCpu(buffer)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W encodeCpu() { empty buffer }`() {
        // Given
        val buffer = emptyList<RumTimeseriesCpuEvent.Data>()

        // When
        val result = DeltaEncoder.encodeCpu(buffer)

        // Then
        assertThat(result).isNull()
    }
}
