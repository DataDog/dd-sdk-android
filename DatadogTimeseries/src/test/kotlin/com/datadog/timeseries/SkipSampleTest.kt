package com.datadog.timeseries

import com.datadog.timeseries.core.TimeseriesConfig
import com.datadog.timeseries.dataprovider.CSVDataProvider
import com.datadog.timeseries.models.TimeseriesName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals

class SkipSampleTest {

    private val config = TimeseriesConfig(
        applicationId = "app-id",
        sessionId = "session-id",
        sessionType = "user",
        source = "ios"
    )

    @Test
    fun `gap in CSV produces fewer data points`() {
        // 5 rows but only 3 are memory_usage (gap at timestamps 2 and 4)
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,100
            2000000000,cpu_usage,10
            3000000000,memory_usage,300
            4000000000,cpu_usage,20
            5000000000,memory_usage,500
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = TimeseriesName.MEMORY_USAGE,
            batchSize = 5
        )

        val results = pipeline.processAll()
        assertEquals(1, results.size) // 3 samples < batchSize 5 -> 1 remaining batch

        val json = Json.parseToJsonElement(results[0]).jsonObject
        val ts = json["timeseries"]!!.jsonObject
        val data = ts["data"]!!.jsonArray

        assertEquals(3, data.size, "Only 3 memory_usage samples, gap reflected")
        assertEquals(1000000000L, data[0].jsonObject["timestamp"]!!.jsonPrimitive.long)
        assertEquals(3000000000L, data[1].jsonObject["timestamp"]!!.jsonPrimitive.long) // gap: 2s jumped
        assertEquals(5000000000L, data[2].jsonObject["timestamp"]!!.jsonPrimitive.long)
    }

    @Test
    fun `malformed rows skipped`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,100
            not_a_number,memory_usage,bad
            3000000000,memory_usage,300
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = TimeseriesName.MEMORY_USAGE,
            batchSize = 5
        )

        val results = pipeline.processAll()
        assertEquals(1, results.size)

        val json = Json.parseToJsonElement(results[0]).jsonObject
        val ts = json["timeseries"]!!.jsonObject
        val data = ts["data"]!!.jsonArray

        assertEquals(2, data.size, "Malformed row skipped")
    }

    @Test
    fun `timestamps reflect gap`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,100
            5000000000,memory_usage,500
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = TimeseriesName.MEMORY_USAGE,
            batchSize = 5
        )

        val results = pipeline.processAll()
        val json = Json.parseToJsonElement(results[0]).jsonObject
        val ts = json["timeseries"]!!.jsonObject

        assertEquals(1000000000L, ts["start"]!!.jsonPrimitive.long)
        assertEquals(5000000000L, ts["end"]!!.jsonPrimitive.long)
    }
}
