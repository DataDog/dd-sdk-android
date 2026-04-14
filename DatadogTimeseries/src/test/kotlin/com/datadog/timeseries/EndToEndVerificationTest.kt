package com.datadog.timeseries

import com.datadog.timeseries.core.TimeseriesConfig
import com.datadog.timeseries.dataprovider.CSVDataProvider
import com.datadog.timeseries.models.TimeseriesName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EndToEndVerificationTest {

    private val config = TimeseriesConfig(
        applicationId = "00000000-0000-0000-0000-000000000000",
        sessionId = "00000000-0000-0000-0000-000000000000",
        sessionType = "user",
        source = "ios"
    )

    // --- Memory ---

    @Test
    fun `memory batch 1 matches fixture`() {
        val actual = processMetric(TimeseriesName.MEMORY_USAGE, batchIndex = 0)
        val expected = loadFixture("expected_memory_batch1")
        assertJsonEquals(expected, actual)
    }

    @Test
    fun `memory batch 2 matches fixture`() {
        val actual = processMetric(TimeseriesName.MEMORY_USAGE, batchIndex = 1)
        val expected = loadFixture("expected_memory_batch2")
        assertJsonEquals(expected, actual)
    }

    // --- CPU ---

    @Test
    fun `CPU batch 1 matches fixture`() {
        val actual = processMetric(TimeseriesName.CPU_USAGE, batchIndex = 0)
        val expected = loadFixture("expected_cpu_batch1")
        assertJsonEquals(expected, actual)
    }

    @Test
    fun `CPU batch 2 matches fixture`() {
        val actual = processMetric(TimeseriesName.CPU_USAGE, batchIndex = 1)
        val expected = loadFixture("expected_cpu_batch2")
        assertJsonEquals(expected, actual)
    }

    // --- Structural validation ---

    @Test
    fun `output contains required fields`() {
        val results = runPipeline(TimeseriesName.MEMORY_USAGE)
        for (jsonStr in results) {
            val json = Json.parseToJsonElement(jsonStr).jsonObject

            assertNotNull(json["_dd"])
            assertNotNull(json["application"])
            assertNotNull(json["date"])
            assertNotNull(json["session"])
            assertNotNull(json["source"])
            assertNotNull(json["timeseries"])
            assertEquals("timeseries", json["type"]!!.jsonPrimitive.content)

            val dd = json["_dd"]!!.jsonObject
            assertEquals(2, dd["format_version"]!!.jsonPrimitive.int)

            val session = json["session"]!!.jsonObject
            assertEquals("user", session["type"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `memory produces two batches`() {
        val results = runPipeline(TimeseriesName.MEMORY_USAGE)
        assertEquals(2, results.size, "10 samples / batchSize 5 = 2 batches")
    }

    @Test
    fun `CPU produces two batches`() {
        val results = runPipeline(TimeseriesName.CPU_USAGE)
        assertEquals(2, results.size, "10 samples / batchSize 5 = 2 batches")
    }

    // --- Helpers ---

    private fun runPipeline(metric: TimeseriesName): List<String> {
        val csvContent = loadResource("fixtures/input_memory_cpu.csv")
        val provider = CSVDataProvider(csvContent, metric)
        val pipeline = TimeseriesPipeline(
            provider = provider,
            config = config,
            metricName = metric,
            batchSize = 5
        )
        return pipeline.processAll()
    }

    private fun processMetric(metric: TimeseriesName, batchIndex: Int): JsonElement {
        val results = runPipeline(metric)
        val jsonStr = maskUUIDs(results[batchIndex])
        return Json.parseToJsonElement(jsonStr)
    }

    private fun loadFixture(name: String): JsonElement {
        val content = loadResource("fixtures/$name.json").trim()
        return Json.parseToJsonElement(content)
    }

    private fun loadResource(path: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return stream.bufferedReader().readText()
    }

    private fun maskUUIDs(input: String): String {
        val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
        return pattern.replace(input, "00000000-0000-0000-0000-000000000000")
    }

    private fun assertJsonEquals(expected: JsonElement, actual: JsonElement, path: String = "$") {
        when {
            expected is JsonObject && actual is JsonObject -> {
                assertEquals(
                    expected.keys, actual.keys,
                    "Keys differ at $path: expected=${expected.keys}, actual=${actual.keys}"
                )
                for (key in expected.keys) {
                    assertJsonEquals(expected[key]!!, actual[key]!!, "$path.$key")
                }
            }
            expected is JsonArray && actual is JsonArray -> {
                assertEquals(
                    expected.size, actual.size,
                    "Array size differs at $path: expected=${expected.size}, actual=${actual.size}"
                )
                for (i in expected.indices) {
                    assertJsonEquals(expected[i], actual[i], "$path[$i]")
                }
            }
            expected is JsonPrimitive && actual is JsonPrimitive -> {
                if (expected.isString && actual.isString) {
                    assertEquals(expected.content, actual.content, "String value differs at $path")
                } else if (!expected.isString && !actual.isString) {
                    // Compare numeric values: try long first, then double
                    val expLong = expected.content.toLongOrNull()
                    val actLong = actual.content.toLongOrNull()
                    if (expLong != null && actLong != null) {
                        assertEquals(expLong, actLong, "Long value differs at $path")
                    } else {
                        val expDouble = expected.content.toDoubleOrNull()
                        val actDouble = actual.content.toDoubleOrNull()
                        if (expDouble != null && actDouble != null) {
                            assertEquals(expDouble, actDouble, "Double value differs at $path")
                        } else {
                            assertEquals(expected.content, actual.content, "Primitive value differs at $path")
                        }
                    }
                } else {
                    assertEquals(expected, actual, "Primitive type differs at $path")
                }
            }
            expected is JsonNull && actual is JsonNull -> { /* both null, ok */ }
            else -> assertEquals(expected, actual, "Type mismatch at $path")
        }
    }
}
