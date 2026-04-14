package com.datadog.timeseries

import com.datadog.timeseries.core.TimeseriesConfig
import com.datadog.timeseries.core.TimeseriesEventBuilder
import com.datadog.timeseries.models.Sample
import com.datadog.timeseries.models.TimeseriesName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeseriesEventBuilderTest {

    private val config = TimeseriesConfig(
        applicationId = "app-123",
        sessionId = "session-456",
        sessionType = "user",
        source = "ios",
        service = "test-service",
        version = "2.0.0"
    )

    @Test
    fun `builds event with correct envelope`() {
        val builder = TimeseriesEventBuilder(config)
        val samples = listOf(
            Sample(timestamp = 5_000_000_000, value = 100.0),
            Sample(timestamp = 6_000_000_000, value = 200.0)
        )

        val event = builder.build(samples, TimeseriesName.MEMORY_USAGE, "evt-id")

        assertEquals(2, event.dd.formatVersion)
        assertEquals("app-123", event.application.id)
        assertEquals("session-456", event.session.id)
        assertEquals("user", event.session.type)
        assertEquals("ios", event.source)
        assertEquals("timeseries", event.type)
        assertEquals("test-service", event.service)
        assertEquals("2.0.0", event.version)
    }

    @Test
    fun `builds event with correct timeseries`() {
        val builder = TimeseriesEventBuilder(config)
        val samples = listOf(
            Sample(timestamp = 5_000_000_000, value = 100.0),
            Sample(timestamp = 6_000_000_000, value = 200.0),
            Sample(timestamp = 7_000_000_000, value = 300.0)
        )

        val event = builder.build(samples, TimeseriesName.CPU_USAGE, "my-uuid")

        assertEquals("my-uuid", event.timeseries.id)
        assertEquals(TimeseriesName.CPU_USAGE, event.timeseries.name)
        assertEquals(5_000_000_000, event.timeseries.start)
        assertEquals(7_000_000_000, event.timeseries.end)
        assertEquals(3, event.timeseries.data.size)
    }

    @Test
    fun `date is start timestamp converted to milliseconds`() {
        val builder = TimeseriesEventBuilder(config)
        val samples = listOf(
            Sample(timestamp = 1_773_055_068_831_000_000, value = 42.0)
        )

        val event = builder.build(samples, TimeseriesName.MEMORY_USAGE, "id")

        assertEquals(1_773_055_068_831, event.date)
    }

    @Test
    fun `data points match samples`() {
        val builder = TimeseriesEventBuilder(config)
        val samples = listOf(
            Sample(timestamp = 1000, value = 42.5),
            Sample(timestamp = 2000, value = 99.9)
        )

        val event = builder.build(samples, TimeseriesName.MEMORY_USAGE, "id")

        assertEquals(1000L, event.timeseries.data[0].timestamp)
        assertEquals(42.5, event.timeseries.data[0].dataPointValue)
        assertEquals(2000L, event.timeseries.data[1].timestamp)
        assertEquals(99.9, event.timeseries.data[1].dataPointValue)
    }

    @Test
    fun `nil service and version`() {
        val configNoOptionals = TimeseriesConfig(
            applicationId = "app",
            sessionId = "sess",
            sessionType = "user",
            source = "ios"
        )
        val builder = TimeseriesEventBuilder(configNoOptionals)
        val event = builder.build(
            listOf(Sample(timestamp = 1, value = 1.0)),
            TimeseriesName.CPU_USAGE,
            "id"
        )

        assertNull(event.service)
        assertNull(event.version)
    }
}
