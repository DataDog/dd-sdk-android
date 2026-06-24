/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class TimeseriesCpuEventAssert private constructor(
    actual: TimeseriesCpuEvent,
    private val json: JsonObject
) : AbstractObjectAssert<TimeseriesCpuEventAssert, TimeseriesCpuEvent>(
    actual,
    TimeseriesCpuEventAssert::class.java
) {

    fun hasApplicationId(expected: String): TimeseriesCpuEventAssert = apply {
        assertThat(actual.application.id)
            .overridingErrorMessage {
                "Expected event to have application.id $expected but was ${actual.application.id}"
            }
            .isEqualTo(expected)
    }

    fun hasSessionId(expected: String): TimeseriesCpuEventAssert = apply {
        assertThat(actual.session.id)
            .overridingErrorMessage { "Expected event to have session.id $expected but was ${actual.session.id}" }
            .isEqualTo(expected)
    }

    fun hasSessionType(expected: TimeseriesCpuEvent.Type): TimeseriesCpuEventAssert = apply {
        assertThat(actual.session.type)
            .overridingErrorMessage { "Expected event to have session.type $expected but was ${actual.session.type}" }
            .isEqualTo(expected)
    }

    fun hasDate(expected: Long): TimeseriesCpuEventAssert = apply {
        assertThat(actual.date)
            .overridingErrorMessage { "Expected event to have date $expected but was ${actual.date}" }
            .isEqualTo(expected)
    }

    fun hasTimeseriesSchema(expected: TimeseriesCpuEvent.Schema): TimeseriesCpuEventAssert = apply {
        assertThat(actual.timeseries.schema)
            .overridingErrorMessage {
                "Expected event to have timeseries.schema $expected but was ${actual.timeseries.schema}"
            }
            .isEqualTo(expected)
    }

    fun hasTimeseriesStart(expected: Long): TimeseriesCpuEventAssert = apply {
        assertThat(actual.timeseries.start)
            .overridingErrorMessage {
                "Expected event to have timeseries.start $expected but was ${actual.timeseries.start}"
            }
            .isEqualTo(expected)
    }

    fun hasTimeseriesEnd(expected: Long): TimeseriesCpuEventAssert = apply {
        assertThat(actual.timeseries.end)
            .overridingErrorMessage {
                "Expected event to have timeseries.end $expected but was ${actual.timeseries.end}"
            }
            .isEqualTo(expected)
    }

    fun hasValidTimeseriesId(): TimeseriesCpuEventAssert = apply {
        assertDoesNotThrow("Expected timeseries.id to be a valid UUID but was ${actual.timeseries.id}") {
            UUID.fromString(actual.timeseries.id)
        }
    }

    fun hasTimeseriesDataCount(expected: Int): TimeseriesCpuEventAssert = apply {
        assertThat(actual.timeseries.data)
            .overridingErrorMessage {
                "Expected event to have timeseries.data of size $expected but was ${actual.timeseries.data.size}"
            }
            .hasSize(expected)
    }

    fun hasCpuUsage(expected: Double, offset: Offset<Double>, position: Int = 0): TimeseriesCpuEventAssert = apply {
        val cpuUsage = actual.timeseries.data.getOrNull(position)?.dataPoint?.cpuUsage?.toDouble()
        assertThat(cpuUsage)
            .overridingErrorMessage { "Expected first data point cpu_usage to be close to $expected but was $cpuUsage" }
            .isNotNull()
            .isCloseTo(expected, offset)
    }

    // region Delta Compression

    // <DOGFOODING> Delta compression is not GA — these assertions cover the delta-scalar wire
    // format used during dogfooding only. This section will be revisited or removed before GA.
    // Use assertThatDelta(JsonObject) to enter this path.

    fun hasDeltaPrecision(expected: Int): TimeseriesCpuEventAssert = apply {
        val precision = json.timeseries.getAsJsonObject("data").get("precision").asInt
        assertThat(precision)
            .overridingErrorMessage { "Expected delta data to have precision $expected but was $precision" }
            .isEqualTo(expected)
    }

    fun hasDeltaResolution(expected: String): TimeseriesCpuEventAssert = apply {
        val resolution = json.timeseries.getAsJsonObject("data").get("resolution").asString
        assertThat(resolution)
            .overridingErrorMessage { "Expected delta data to have resolution $expected but was $resolution" }
            .isEqualTo(expected)
    }

    fun hasDeltaTsValues(vararg expected: Long): TimeseriesCpuEventAssert = apply {
        val tsArray = json.timeseries.getAsJsonObject("data").get("ts").asJsonArray
        expected.forEachIndexed { i, v ->
            assertThat(tsArray[i].asLong)
                .overridingErrorMessage { "Expected delta ts[$i] to be $v but was ${tsArray[i].asLong}" }
                .isEqualTo(v)
        }
    }

    fun hasDeltaValueAt(index: Int, expected: Long): TimeseriesCpuEventAssert = apply {
        val valueArray = json.timeseries.getAsJsonObject("data").get("value").asJsonArray
        val actual = valueArray[index].asLong
        assertThat(actual)
            .overridingErrorMessage { "Expected delta value[$index] to be $expected but was $actual" }
            .isEqualTo(expected)
    }

    // endregion

    companion object {
        private val JsonObject.timeseries: JsonObject get() = getAsJsonObject("timeseries")

        internal fun assertThat(event: TimeseriesCpuEvent): TimeseriesCpuEventAssert =
            TimeseriesCpuEventAssert(event, event.toJson() as JsonObject)

        internal fun assertThat(json: JsonObject): TimeseriesCpuEventAssert =
            assertThat(TimeseriesCpuEvent.fromJsonObject(json))

        /**
         * <DOGFOODING> Delta encoding replaces timeseries.data with a JsonObject in-place,
         * making fromJsonObject fail. A patched copy (empty data array) is used to construct
         * the typed model; the original json is retained for delta assertions.
         */
        internal fun assertThatDelta(json: JsonObject): TimeseriesCpuEventAssert {
            val patchedJson = json.deepCopy().also {
                it.getAsJsonObject("timeseries").add("data", JsonArray())
            }
            return TimeseriesCpuEventAssert(TimeseriesCpuEvent.fromJsonObject(patchedJson), json)
        }
    }
}
