package com.datadog.timeseries

import com.datadog.timeseries.models.Sample
import com.datadog.timeseries.models.TimeseriesEvent
import com.datadog.timeseries.models.TimeseriesName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TimeseriesEventModelTest {

    private val json = Json { explicitNulls = false }

    @Test
    fun `encodes to expected JSON structure`() {
        val event = TimeseriesEvent(
            dd = TimeseriesEvent.DD(formatVersion = 2),
            application = TimeseriesEvent.Application(id = "app-id-123"),
            date = 1773055119487,
            session = TimeseriesEvent.Session(id = "session-id-456", type = "user"),
            source = "ios",
            type = "timeseries",
            service = "my-service",
            version = "1.0.0",
            timeseries = TimeseriesEvent.Timeseries(
                id = "ts-id-789",
                name = TimeseriesName.MEMORY_USAGE,
                start = 1773055068831000000,
                end = 1773055082916000000,
                data = listOf(
                    TimeseriesEvent.DataPoint(timestamp = 1773055068831000000, dataPointValue = 38052032.0),
                    TimeseriesEvent.DataPoint(timestamp = 1773055069917000000, dataPointValue = 37970112.0)
                )
            )
        )

        val jsonStr = json.encodeToString(TimeseriesEvent.serializer(), event)
        val parsed = Json.parseToJsonElement(jsonStr).jsonObject

        val dd = parsed["_dd"]!!.jsonObject
        assertEquals(2, dd["format_version"]!!.jsonPrimitive.int)

        val application = parsed["application"]!!.jsonObject
        assertEquals("app-id-123", application["id"]!!.jsonPrimitive.content)

        assertEquals(1773055119487, parsed["date"]!!.jsonPrimitive.long)

        val session = parsed["session"]!!.jsonObject
        assertEquals("session-id-456", session["id"]!!.jsonPrimitive.content)
        assertEquals("user", session["type"]!!.jsonPrimitive.content)

        assertEquals("ios", parsed["source"]!!.jsonPrimitive.content)
        assertEquals("timeseries", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("my-service", parsed["service"]!!.jsonPrimitive.content)
        assertEquals("1.0.0", parsed["version"]!!.jsonPrimitive.content)

        val ts = parsed["timeseries"]!!.jsonObject
        assertEquals("ts-id-789", ts["id"]!!.jsonPrimitive.content)
        assertEquals("memory_usage", ts["name"]!!.jsonPrimitive.content)
        assertEquals(1773055068831000000, ts["start"]!!.jsonPrimitive.long)
        assertEquals(1773055082916000000, ts["end"]!!.jsonPrimitive.long)

        val dataPoints = ts["data"]!!.jsonArray
        assertEquals(2, dataPoints.size)
        assertEquals(1773055068831000000, dataPoints[0].jsonObject["timestamp"]!!.jsonPrimitive.long)
        assertEquals(38052032.0, dataPoints[0].jsonObject["data_point_value"]!!.jsonPrimitive.double)
    }

    @Test
    fun `omits null service and version`() {
        val event = TimeseriesEvent(
            dd = TimeseriesEvent.DD(formatVersion = 2),
            application = TimeseriesEvent.Application(id = "app-id"),
            date = 1000,
            session = TimeseriesEvent.Session(id = "sess-id", type = "user"),
            source = "ios",
            type = "timeseries",
            timeseries = TimeseriesEvent.Timeseries(
                id = "ts-id",
                name = TimeseriesName.CPU_USAGE,
                start = 1000000000,
                end = 2000000000,
                data = listOf(
                    TimeseriesEvent.DataPoint(timestamp = 1000000000, dataPointValue = 55.3)
                )
            )
        )

        val jsonStr = json.encodeToString(TimeseriesEvent.serializer(), event)
        val parsed = Json.parseToJsonElement(jsonStr).jsonObject

        assertFalse(parsed.containsKey("service"))
        assertFalse(parsed.containsKey("version"))
        assertEquals("timeseries", parsed["type"]!!.jsonPrimitive.content)

        val ts = parsed["timeseries"]!!.jsonObject
        assertEquals("cpu_usage", ts["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `TimeseriesName raw values`() {
        assertEquals("memory_usage", TimeseriesName.MEMORY_USAGE.value)
        assertEquals("cpu_usage", TimeseriesName.CPU_USAGE.value)
    }

    @Test
    fun `Sample stores values`() {
        val sample = Sample(timestamp = 5_000_000_000, value = 123.456)
        assertEquals(5_000_000_000, sample.timestamp)
        assertEquals(123.456, sample.value)
    }
}
