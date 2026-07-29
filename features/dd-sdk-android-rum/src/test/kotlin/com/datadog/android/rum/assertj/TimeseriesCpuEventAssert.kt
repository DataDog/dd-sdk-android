/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.model.TimeseriesCpuEvent.TimeseriesCpuEventSessionType
import com.google.gson.JsonObject
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class TimeseriesCpuEventAssert private constructor(
    actual: TimeseriesCpuEvent
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

    fun hasSessionType(expected: TimeseriesCpuEventSessionType): TimeseriesCpuEventAssert = apply {
        assertThat(actual.session.type)
            .overridingErrorMessage { "Expected event to have session.type $expected but was ${actual.session.type}" }
            .isEqualTo(expected)
    }

    fun hasService(expected: String): TimeseriesCpuEventAssert = apply {
        assertThat(actual.service)
            .overridingErrorMessage { "Expected event to have service $expected but was ${actual.service}" }
            .isEqualTo(expected)
    }

    fun hasVersion(expected: String): TimeseriesCpuEventAssert = apply {
        assertThat(actual.version)
            .overridingErrorMessage { "Expected event to have version $expected but was ${actual.version}" }
            .isEqualTo(expected)
    }

    fun hasDate(expected: Long): TimeseriesCpuEventAssert = apply {
        assertThat(actual.date)
            .overridingErrorMessage { "Expected event to have date $expected but was ${actual.date}" }
            .isEqualTo(expected)
    }

    fun hasTimeseriesSchema(expected: String): TimeseriesCpuEventAssert = apply {
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
        assertThat(actual.timeseries.data.timestamps)
            .overridingErrorMessage {
                "Expected event to have timeseries.data of size $expected " +
                    "but was ${actual.timeseries.data.timestamps.size}"
            }
            .hasSize(expected)
    }

    fun hasCpuUsage(expected: Double, offset: Offset<Double>, position: Int = 0): TimeseriesCpuEventAssert = apply {
        val cpuUsage = actual.timeseries.data.values.cpuUsage.getOrNull(position)?.toDouble()
        assertThat(cpuUsage)
            .overridingErrorMessage { "Expected first data point cpu_usage to be close to $expected but was $cpuUsage" }
            .isNotNull()
            .isCloseTo(expected, offset)
    }

    companion object {
        internal fun assertThat(event: TimeseriesCpuEvent): TimeseriesCpuEventAssert =
            TimeseriesCpuEventAssert(event)

        internal fun assertThat(json: JsonObject): TimeseriesCpuEventAssert =
            assertThat(TimeseriesCpuEvent.fromJsonObject(json))
    }
}
