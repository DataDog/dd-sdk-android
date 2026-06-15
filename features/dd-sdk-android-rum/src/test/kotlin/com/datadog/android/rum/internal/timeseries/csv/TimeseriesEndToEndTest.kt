/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.utils.forge.Configurator
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigDecimal
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TimeseriesEndToEndTest {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    private lateinit var csvContent: String
    private lateinit var emitted: List<JsonObject>

    @BeforeEach
    fun `set up`() {
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn FIXED_DATE_MS

        csvContent = loadResource("fixtures/timeseries/input_memory_cpu.csv")
        val testedTimeseries = CsvTimeseries.create(
            csvContent = csvContent,
            sessionId = SESSION_ID,
            applicationId = APPLICATION_ID,
            sessionType = RumSessionType.USER,
            totalRamBytes = TOTAL_RAM_BYTES,
            bufferSize = BATCH_SIZE,
            timeProvider = mockTimeProvider
        )
        testedTimeseries.onSessionStart()
        testedTimeseries.onSessionStop()
        emitted = testedTimeseries.captured
    }

    @Test
    fun `M emit two memory and two cpu batches W full CSV is fed { batchSize=5, samples=10 }`() {
        // Then
        val memoryEvents = emitted.filter { metricNameOf(it) == NAME_MEMORY }
        val cpuEvents = emitted.filter { metricNameOf(it) == NAME_CPU }

        assertThat(memoryEvents).hasSize(2)
        assertThat(cpuEvents).hasSize(2)
    }

    @Test
    fun `M match expected fixture W memory batch 1`() {
        val expected = loadFixture("fixtures/timeseries/expected_memory_batch1.json")
        val actual = emitted.filter { metricNameOf(it) == NAME_MEMORY }[0]
        assertJsonEquals(expected, maskTimeseriesId(actual))
    }

    @Test
    fun `M match expected fixture W memory batch 2`() {
        val expected = loadFixture("fixtures/timeseries/expected_memory_batch2.json")
        val actual = emitted.filter { metricNameOf(it) == NAME_MEMORY }[1]
        assertJsonEquals(expected, maskTimeseriesId(actual))
    }

    @Test
    fun `M match expected fixture W cpu batch 1`() {
        val expected = loadFixture("fixtures/timeseries/expected_cpu_batch1.json")
        val actual = emitted.filter { metricNameOf(it) == NAME_CPU }[0]
        assertJsonEquals(expected, maskTimeseriesId(actual))
    }

    @Test
    fun `M match expected fixture W cpu batch 2`() {
        val expected = loadFixture("fixtures/timeseries/expected_cpu_batch2.json")
        val actual = emitted.filter { metricNameOf(it) == NAME_CPU }[1]
        assertJsonEquals(expected, maskTimeseriesId(actual))
    }

    @Test
    fun `M emit valid timeseries id W any batch`() {
        // Each emitted event must carry a non-blank UUID; randomness is not asserted.
        for (event in emitted) {
            val id = event.getAsJsonObject("timeseries").get("id").asString
            assertThat(UUID.fromString(id)).isNotNull
        }
    }

    @Test
    fun `M produce monotonic start lt or eq to end W any batch`() {
        for (event in emitted) {
            val ts = event.getAsJsonObject("timeseries")
            assertThat(ts.get("start").asLong).isLessThanOrEqualTo(ts.get("end").asLong)
        }
    }

    private fun metricNameOf(event: JsonObject): String = event.getAsJsonObject("timeseries").get("name").asString

    private fun maskTimeseriesId(actual: JsonObject): JsonObject {
        val copy = actual.deepCopy()
        copy.getAsJsonObject("timeseries").addProperty("id", MASKED_UUID)
        return copy
    }

    private fun loadFixture(path: String): JsonElement = JsonParser.parseString(loadResource(path))

    private fun loadResource(path: String): String {
        val stream = this::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }

    /**
     * Structural JSON comparison that ignores key insertion order and treats numerically
     * equal primitives as equal regardless of literal representation
     * (e.g. `31233300` vs `3.12333E7`).
     */
    private fun assertJsonEquals(expected: JsonElement, actual: JsonElement, path: String = "$") {
        when {
            expected is JsonObject && actual is JsonObject -> {
                val expectedKeys: Set<String> = expected.keySet()
                val actualKeys: Set<String> = actual.keySet()
                assertThat(actualKeys.toList())
                    .describedAs("Keys at %s", path)
                    .containsExactlyInAnyOrderElementsOf(expectedKeys.toList())
                for (key in expectedKeys) {
                    assertJsonEquals(expected.get(key), actual.get(key), "$path.$key")
                }
            }
            expected is JsonArray && actual is JsonArray -> {
                assertThat(actual.size())
                    .describedAs("Array size at %s", path)
                    .isEqualTo(expected.size())
                for (i in 0 until expected.size()) {
                    assertJsonEquals(expected.get(i), actual.get(i), "$path[$i]")
                }
            }
            expected is JsonPrimitive && actual is JsonPrimitive -> {
                assertPrimitiveEquals(expected, actual, path)
            }
            expected is JsonNull && actual is JsonNull -> Unit
            else -> assertThat(actual)
                .describedAs("Type mismatch at %s", path)
                .isEqualTo(expected)
        }
    }

    private fun assertPrimitiveEquals(expected: JsonPrimitive, actual: JsonPrimitive, path: String) {
        when {
            expected.isString != actual.isString ->
                fail(
                    "JSON type mismatch at $path: expected ${if (expected.isString) "string" else "number/boolean"}," +
                        " got ${if (actual.isString) "string" else "number/boolean"}" +
                        " (values: ${expected.asString} vs ${actual.asString})"
                )
            expected.isBoolean != actual.isBoolean ->
                fail(
                    "JSON type mismatch at $path: expected ${if (expected.isBoolean) "boolean" else "non-boolean"}," +
                        " got ${if (actual.isBoolean) "boolean" else "non-boolean"}" +
                        " (values: ${expected.asString} vs ${actual.asString})"
                )
            expected.isString ->
                assertThat(actual.asString).describedAs("String at %s", path).isEqualTo(expected.asString)
            expected.isBoolean ->
                assertThat(actual.asBoolean).describedAs("Boolean at %s", path).isEqualTo(expected.asBoolean)
            else -> assertNumericPrimitivesEqual(expected, actual, path)
        }
    }

    // Numeric: integer-typed values must match exactly; floating-point values are compared
    // with a small relative tolerance to absorb 64-bit FP rounding (e.g. computed
    // memory_percent of 3.1233299999999997 vs expected 3.12333).
    private fun assertNumericPrimitivesEqual(expected: JsonPrimitive, actual: JsonPrimitive, path: String) {
        val expectedBd = expected.asString.toBigDecimalOrNull()
            ?: error("Expected numeric value at $path, got: ${expected.asString}")
        val actualBd = actual.asString.toBigDecimalOrNull()
            ?: error("Actual numeric value at $path is not a number: ${actual.asString}")

        val isFloat = expected.asString.contains('.') ||
            expected.asString.contains('e', ignoreCase = true) ||
            actual.asString.contains('.') ||
            actual.asString.contains('e', ignoreCase = true)

        if (isFloat) {
            val expectedDouble = expectedBd.toDouble()
            val actualDouble = actualBd.toDouble()
            assertThat(actualDouble)
                .describedAs("Double at %s: expected=%s, actual=%s", path, expectedDouble, actualDouble)
                .isCloseTo(expectedDouble, org.assertj.core.data.Offset.offset(FP_TOLERANCE))
        } else {
            assertThat(actualBd.compareTo(expectedBd))
                .describedAs("Integer at %s: expected=%s, actual=%s", path, expectedBd, actualBd)
                .isZero
        }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (e: NumberFormatException) {
        null
    }

    companion object {
        private const val SESSION_ID: String = "fake-session-id"
        private const val APPLICATION_ID: String = "fake-app-id"
        private const val FIXED_DATE_MS: Long = 1_700_000_001_000L
        private const val TOTAL_RAM_BYTES: Long = 1_000_000_000L
        private const val BATCH_SIZE: Int = 5
        private const val NAME_MEMORY: String = "memory"
        private const val NAME_CPU: String = "cpu"
        private const val MASKED_UUID: String = "00000000-0000-0000-0000-000000000000"
        private const val FP_TOLERANCE: Double = 1e-9
    }
}
