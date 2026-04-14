package com.datadog.timeseries

import com.datadog.timeseries.core.TimeseriesConfig
import com.datadog.timeseries.dataprovider.CSVDataProvider
import com.datadog.timeseries.models.TimeseriesName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeseriesPipelineTest {

    private val config = TimeseriesConfig(
        applicationId = "app-id",
        sessionId = "session-id",
        sessionType = "user",
        source = "ios"
    )

    @Test
    fun `processAll produces correct number of batches`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,100
            2000000000,memory_usage,200
            3000000000,memory_usage,300
            4000000000,memory_usage,400
            5000000000,memory_usage,500
            6000000000,memory_usage,600
            7000000000,memory_usage,700
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = TimeseriesName.MEMORY_USAGE,
            batchSize = 3
        )

        val results = pipeline.processAll()
        // 7 samples / 3 batch size = 2 full batches + 1 remaining batch (1 sample)
        assertEquals(3, results.size)
    }

    @Test
    fun `processAll produces valid JSON`() {
        val csv = """
            timestamp,metric,value
            1000000000,memory_usage,100
            2000000000,memory_usage,200
        """.trimIndent()

        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = TimeseriesName.MEMORY_USAGE,
            batchSize = 5
        )

        val results = pipeline.processAll()
        // 2 samples < batchSize 5 -> 1 remaining batch
        assertEquals(1, results.size)

        val json = Json.parseToJsonElement(results[0]).jsonObject
        assertEquals("timeseries", json["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `empty provider produces no output`() {
        val csv = "timestamp,metric,value\n"
        val provider = CSVDataProvider(csv, TimeseriesName.MEMORY_USAGE)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = TimeseriesName.MEMORY_USAGE,
            batchSize = 5
        )

        val results = pipeline.processAll()
        assertTrue(results.isEmpty())
    }
}
