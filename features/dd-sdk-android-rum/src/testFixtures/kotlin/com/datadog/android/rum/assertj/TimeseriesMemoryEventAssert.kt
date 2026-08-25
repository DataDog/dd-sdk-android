/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent.TimeseriesMemoryEventSessionType
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import java.util.UUID

class TimeseriesMemoryEventAssert private constructor(
    private val actual: TimeseriesMemoryEvent
) {

    fun hasApplicationId(expected: String) = apply {
        assertThat(actual.application.id)
            .overridingErrorMessage {
                "Expected event to have application.id $expected " +
                    "but was ${actual.application.id}"
            }
            .isEqualTo(expected)
    }

    fun hasSessionId(expected: String) = apply {
        assertThat(actual.session.id)
            .overridingErrorMessage {
                "Expected event to have session.id $expected but was ${actual.session.id}"
            }
            .isEqualTo(expected)
    }

    fun hasSessionType(expected: TimeseriesMemoryEventSessionType) = apply {
        assertThat(actual.session.type)
            .overridingErrorMessage {
                "Expected event to have session.type $expected but was ${actual.session.type}"
            }
            .isEqualTo(expected)
    }

    fun hasService(expected: String) = apply {
        assertThat(actual.service)
            .overridingErrorMessage { "Expected event to have service $expected but was ${actual.service}" }
            .isEqualTo(expected)
    }

    fun hasVersion(expected: String) = apply {
        assertThat(actual.version)
            .overridingErrorMessage { "Expected event to have version $expected but was ${actual.version}" }
            .isEqualTo(expected)
    }

    fun hasDate(expected: Long) = apply {
        assertThat(actual.date)
            .overridingErrorMessage { "Expected event to have date $expected but was ${actual.date}" }
            .isEqualTo(expected)
    }

    fun hasTimeseriesName(expected: String) = apply {
        assertThat(actual.timeseries.name)
            .overridingErrorMessage {
                "Expected event to have timeseries.name $expected but was ${actual.timeseries.name}"
            }
            .isEqualTo(expected)
    }

    fun hasTimeseriesSchema(expected: String) = apply {
        assertThat(actual.timeseries.schema)
            .overridingErrorMessage {
                "Expected event to have timeseries.schema $expected but was ${actual.timeseries.schema}"
            }
            .isEqualTo(expected)
    }

    fun hasTimeseriesStart(expected: Long) = apply {
        assertThat(actual.timeseries.start)
            .overridingErrorMessage {
                "Expected event to have timeseries.start $expected but was ${actual.timeseries.start}"
            }
            .isEqualTo(expected)
    }

    fun hasTimeseriesEnd(expected: Long) = apply {
        assertThat(actual.timeseries.end)
            .overridingErrorMessage {
                "Expected event to have timeseries.end $expected but was ${actual.timeseries.end}"
            }
            .isEqualTo(expected)
    }

    fun hasTimeseriesStartNotAfterEnd() = apply {
        assertThat(actual.timeseries.start)
            .overridingErrorMessage {
                "Expected timeseries.start ${actual.timeseries.start} to be less than or equal to " +
                    "timeseries.end ${actual.timeseries.end}"
            }
            .isLessThanOrEqualTo(actual.timeseries.end)
    }

    fun hasValidTimeseriesId() = apply {
        val parsedTimeseriesId = runCatching { UUID.fromString(actual.timeseries.id) }.getOrNull()

        assertThat(parsedTimeseriesId)
            .overridingErrorMessage { "Expected timeseries.id to be a valid UUID but was ${actual.timeseries.id}" }
            .isNotNull()
    }

    fun hasSameTimeseriesAs(expected: TimeseriesMemoryEvent) = apply {
        val expectedTimeseries = expected.timeseries
        val expectedMemoryFootprint = expectedTimeseries.data.values.memoryFootprint
        val actualMemoryFootprint = actual.timeseries.data.values.memoryFootprint
        val expectedMemoryPercent = expectedTimeseries.data.values.memoryPercent
        val actualMemoryPercent = actual.timeseries.data.values.memoryPercent

        hasTimeseriesName(expectedTimeseries.name)
            .hasTimeseriesSchema(expectedTimeseries.schema)
            .hasTimeseriesStart(expectedTimeseries.start)
            .hasTimeseriesEnd(expectedTimeseries.end)
            .hasTimestamps(*expectedTimeseries.data.timestamps.toLongArray())

        assertThat(actualMemoryFootprint)
            .overridingErrorMessage {
                "Expected memory footprint values count ${expectedMemoryFootprint.size} " +
                    "but was ${actualMemoryFootprint.size}"
            }
            .hasSameSizeAs(expectedMemoryFootprint)
        assertThat(actualMemoryPercent)
            .overridingErrorMessage {
                "Expected memory percent values count ${expectedMemoryPercent.size} " +
                    "but was ${actualMemoryPercent.size}"
            }
            .hasSameSizeAs(expectedMemoryPercent)
        expectedMemoryFootprint.forEachIndexed { position, expectedValue ->
            hasMemoryFootprint(expectedValue.toDouble(), NUMBER_COMPARISON_TOLERANCE, position)
        }
        expectedMemoryPercent.forEachIndexed { position, expectedValue ->
            hasMemoryPercent(expectedValue.toDouble(), NUMBER_COMPARISON_TOLERANCE, position)
        }
    }

    fun hasDataPointsCount(expected: Int) = apply {
        val timestampsCount = actual.timeseries.data.timestamps.size

        assertThat(timestampsCount)
            .overridingErrorMessage {
                "Expected timeseries data to contain $expected datapoints but contained $timestampsCount"
            }
            .isEqualTo(expected)
    }

    fun hasTimestamps(vararg expected: Long) = apply {
        assertThat(actual.timeseries.data.timestamps)
            .overridingErrorMessage {
                "Expected timeseries timestamps ${expected.toList()} but was ${actual.timeseries.data.timestamps}"
            }
            .containsExactlyElementsOf(expected.toList())
    }

    fun hasMemoryFootprint(expected: Double, offset: Double, position: Int = 0) = apply {
        val memoryFootprint = actual.timeseries.data.values.memoryFootprint.getOrNull(position)?.toDouble()

        assertThat(memoryFootprint)
            .overridingErrorMessage {
                "Expected memory footprint data point at position $position to be present"
            }
            .isNotNull()
        assertThat(checkNotNull(memoryFootprint))
            .overridingErrorMessage {
                "Expected memory footprint at position $position to be close to $expected but was $memoryFootprint"
            }
            .isCloseTo(expected, Offset.offset(offset))
    }

    fun hasMemoryPercent(expected: Double, offset: Double, position: Int = 0) = apply {
        val memoryPercent = actual.timeseries.data.values.memoryPercent.getOrNull(position)?.toDouble()

        assertThat(memoryPercent)
            .overridingErrorMessage {
                "Expected memory percent data point at position $position to be present"
            }
            .isNotNull()
        assertThat(checkNotNull(memoryPercent))
            .overridingErrorMessage {
                "Expected memory percent at position $position to be close to $expected but was $memoryPercent"
            }
            .isCloseTo(expected, Offset.offset(offset))
    }

    companion object {
        private const val NUMBER_COMPARISON_TOLERANCE = 1e-9

        fun assertThat(actual: TimeseriesMemoryEvent?): TimeseriesMemoryEventAssert {
            Assertions.assertThat(actual)
                .overridingErrorMessage { "Expected timeseries event to be present" }
                .isNotNull()
            return TimeseriesMemoryEventAssert(checkNotNull(actual))
        }
    }
}
