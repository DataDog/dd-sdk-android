package com.datadog.timeseries

import com.datadog.timeseries.encoding.TimeseriesEncoder
import com.datadog.timeseries.models.TimeseriesEvent
import com.datadog.timeseries.models.TimeseriesName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeseriesEncoderTest {

    @Test
    fun `produces snake_case keys`() {
        val event = makeSimpleEvent()
        val encoder = TimeseriesEncoder()
        val jsonStr = encoder.encode(event)

        assertTrue(jsonStr.contains("\"format_version\""))
        assertTrue(jsonStr.contains("\"data_point_value\""))
        assertFalse(jsonStr.contains("\"formatVersion\""))
        assertFalse(jsonStr.contains("\"dataPointValue\""))
    }

    @Test
    fun `produces _dd key`() {
        val event = makeSimpleEvent()
        val encoder = TimeseriesEncoder()
        val jsonStr = encoder.encode(event)

        assertTrue(jsonStr.contains("\"_dd\""))
    }

    @Test
    fun `produces valid JSON`() {
        val event = makeSimpleEvent()
        val encoder = TimeseriesEncoder()
        val jsonStr = encoder.encode(event)

        val parsed = Json.parseToJsonElement(jsonStr)
        assertNotNull(parsed as? JsonObject)
    }

    @Test
    fun `deterministic output`() {
        val event = makeSimpleEvent()
        val encoder = TimeseriesEncoder()
        val json1 = encoder.encode(event)
        val json2 = encoder.encode(event)

        assertEquals(json1, json2, "Encoding the same event should produce identical output")
    }

    private fun makeSimpleEvent(): TimeseriesEvent {
        return TimeseriesEvent(
            dd = TimeseriesEvent.DD(formatVersion = 2),
            application = TimeseriesEvent.Application(id = "app"),
            date = 1000,
            session = TimeseriesEvent.Session(id = "sess", type = "user"),
            source = "ios",
            type = "timeseries",
            timeseries = TimeseriesEvent.Timeseries(
                id = "ts-id",
                name = TimeseriesName.MEMORY_USAGE,
                start = 1_000_000_000,
                end = 2_000_000_000,
                data = listOf(
                    TimeseriesEvent.DataPoint(timestamp = 1_000_000_000, dataPointValue = 42.0)
                )
            )
        )
    }
}
