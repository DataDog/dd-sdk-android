/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.TimeseriesCpuEventAssert
import com.datadog.android.rum.assertj.TimeseriesMemoryEventAssert
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TimeseriesCollectorEndToEndTest {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    private lateinit var capturedMemoryEvents: List<TimeseriesMemoryEvent>
    private lateinit var capturedCpuEvents: List<TimeseriesCpuEvent>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn FIXED_DATE_MS

        val testedTimeseriesCollector = CsvCollector.create(
            csvContent = loadResource("fixtures/timeseries/input_memory_cpu.csv"),
            sessionType = RumSessionType.USER,
            totalRamBytes = TOTAL_RAM_BYTES,
            bufferSize = BATCH_SIZE,
            timeProvider = mockTimeProvider,
            forge = forge,
            internalLogger = mock()
        )
        testedTimeseriesCollector.onSessionStart()
        capturedMemoryEvents = testedTimeseriesCollector.captured.mapNotNull { serializedPayload ->
            runCatching { TimeseriesMemoryEvent.fromJsonObject(serializedPayload) }
                .getOrNull()
        }
        capturedCpuEvents = testedTimeseriesCollector.captured.mapNotNull { serializedPayload ->
            runCatching { TimeseriesCpuEvent.fromJsonObject(serializedPayload) }
                .getOrNull()
        }
    }

    @Test
    fun `M emit two memory and two cpu batches W full CSV is fed { batchSize=5, samples=10 }`() {
        // Then
        assertThat(capturedMemoryEvents.size).isEqualTo(2)
        assertThat(capturedCpuEvents.size).isEqualTo(2)
    }

    @Test
    fun `M match expected timeseries fixture W memory batch 1`() {
        val expected = loadExpectedMemoryTimeseries("fixtures/timeseries/expected_memory_batch1.json")
        val actual = capturedMemoryEvents[0]

        TimeseriesMemoryEventAssert.assertThat(actual)
            .hasDate(expected.date)
            .hasSameTimeseriesAs(expected)
    }

    @Test
    fun `M match expected timeseries fixture W memory batch 2`() {
        val expected = loadExpectedMemoryTimeseries("fixtures/timeseries/expected_memory_batch2.json")
        val actual = capturedMemoryEvents[1]

        TimeseriesMemoryEventAssert.assertThat(actual)
            .hasDate(expected.date)
            .hasSameTimeseriesAs(expected)
    }

    @Test
    fun `M match expected timeseries fixture W cpu batch 1`() {
        val expected = loadExpectedCpuTimeseries("fixtures/timeseries/expected_cpu_batch1.json")
        val actual = capturedCpuEvents[0]

        TimeseriesCpuEventAssert.assertThat(actual)
            .hasDate(expected.date)
            .hasSameTimeseriesAs(expected)
    }

    @Test
    fun `M match expected timeseries fixture W cpu batch 2`() {
        val expected = loadExpectedCpuTimeseries("fixtures/timeseries/expected_cpu_batch2.json")
        val actual = capturedCpuEvents[1]

        TimeseriesCpuEventAssert.assertThat(actual)
            .hasDate(expected.date)
            .hasSameTimeseriesAs(expected)
    }

    @Test
    fun `M emit valid timeseries id W any batch`() {
        capturedMemoryEvents.forEach { actual ->
            TimeseriesMemoryEventAssert.assertThat(actual)
                .hasValidTimeseriesId()
        }
        capturedCpuEvents.forEach { actual ->
            TimeseriesCpuEventAssert.assertThat(actual)
                .hasValidTimeseriesId()
        }
    }

    @Test
    fun `M produce monotonic start lt or eq to end W any batch`() {
        capturedMemoryEvents.forEach { actual ->
            TimeseriesMemoryEventAssert.assertThat(actual)
                .hasTimeseriesStartNotAfterEnd()
        }
        capturedCpuEvents.forEach { actual ->
            TimeseriesCpuEventAssert.assertThat(actual)
                .hasTimeseriesStartNotAfterEnd()
        }
    }

    private companion object {
        private const val FIXED_DATE_MS: Long = 1_700_000_001_000L
        private const val TOTAL_RAM_BYTES: Long = 1_000_000_000L
        private const val BATCH_SIZE: Int = 5
        private fun loadExpectedMemoryTimeseries(path: String): TimeseriesMemoryEvent =
            TimeseriesMemoryEvent.fromJson(loadResource(path))

        private fun loadExpectedCpuTimeseries(path: String): TimeseriesCpuEvent =
            TimeseriesCpuEvent.fromJson(loadResource(path))

        private fun loadResource(path: String): String {
            val stream = this::class.java.classLoader?.getResourceAsStream(path)
                ?: error("Resource not found: $path")
            return stream.bufferedReader().use { it.readText() }
        }
    }
}
