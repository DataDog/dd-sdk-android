/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.model.ViewEvent
import java.util.concurrent.TimeUnit
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.assertj.core.data.Percentage

internal class ViewEventAssert(actual: ViewEvent) :
    AbstractObjectAssert<ViewEventAssert, ViewEvent>(
        actual,
        ViewEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = TIMESTAMP_THRESHOLD_MS
    ): ViewEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasName(expected: String): ViewEventAssert {
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expected but was ${actual.view.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUrl(expected: String): ViewEventAssert {
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expected but was ${actual.view.url}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDuration(
        expected: Long
    ): ViewEventAssert {
        assertThat(actual.view.timeSpent)
            .overridingErrorMessage(
                "Expected event data to have view.time_spent $expected " +
                    "but was ${actual.view.timeSpent}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationLowerThan(upperBound: Long): ViewEventAssert {
        assertThat(actual.view.timeSpent)
            .overridingErrorMessage(
                "Expected event data to have view.time_spent lower than $upperBound " +
                    "but was ${actual.view.timeSpent}"
            )
            .isLessThanOrEqualTo(upperBound)
        return this
    }

    fun hasDurationGreaterThan(upperBound: Long): ViewEventAssert {
        assertThat(actual.view.timeSpent)
            .overridingErrorMessage(
                "Expected event data to have view.time_spent greater than $upperBound " +
                    "but was ${actual.view.timeSpent}"
            )
            .isGreaterThanOrEqualTo(upperBound)
        return this
    }

    fun hasVersion(expected: Long): ViewEventAssert {
        assertThat(actual.dd.documentVersion)
            .overridingErrorMessage(
                "Expected event data to have dd.documentVersion $expected " +
                    "but was ${actual.dd.documentVersion}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasErrorCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.error.count)
            .overridingErrorMessage(
                "Expected event data to have view.error.count $expected " +
                    "but was ${actual.view.error.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResourceCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.resource.count)
            .overridingErrorMessage(
                "Expected event data to have view.resource.count $expected " +
                    "but was ${actual.view.resource.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.action.count)
            .overridingErrorMessage(
                "Expected event data to have view.action.count $expected " +
                    "but was ${actual.view.action.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCrashCount(expected: Long?): ViewEventAssert {
        assertThat(actual.view.crash?.count)
            .overridingErrorMessage(
                "Expected event data to have view.crash.count $expected " +
                    "but was ${actual.view.crash?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLongTaskCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.longTask?.count)
            .overridingErrorMessage(
                "Expected event data to have view.longTask.count $expected " +
                    "but was ${actual.view.longTask?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFrozenFrameCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.frozenFrame?.count)
            .overridingErrorMessage(
                "Expected event data to have view.frozenFrame.count $expected " +
                    "but was ${actual.view.frozenFrame?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expectedId: String): ViewEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId)
        return this
    }

    fun hasApplicationId(expected: String): ViewEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected context to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ViewEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected context to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLoadingTime(
        expected: Long?
    ): ViewEventAssert {
        assertThat(actual.view.loadingTime)
            .overridingErrorMessage(
                "Expected event to have loadingTime $expected" +
                    " but was ${actual.view.loadingTime}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLoadingType(
        expected: ViewEvent.LoadingType?
    ): ViewEventAssert {
        assertThat(actual.view.loadingType)
            .overridingErrorMessage(
                "Expected event to have loadingType $expected" +
                    " but was ${actual.view.loadingType}"
            )
            .isEqualTo(expected)
        return this
    }

    fun isActive(
        expected: Boolean?
    ): ViewEventAssert {
        assertThat(actual.view.isActive)
            .overridingErrorMessage(
                "Expected event to have isActive $expected" +
                    " but was ${actual.view.isActive}"
            )
            .isEqualTo(expected)
        return this
    }

    fun isSlowRendered(
        expected: Boolean?
    ): ViewEventAssert {
        assertThat(actual.view.isSlowRendered)
            .overridingErrorMessage(
                "Expected event to have isSlowRendered $expected" +
                    " but was ${actual.view.isSlowRendered}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoCustomTimings(): ViewEventAssert {
        assertThat(actual.view.customTimings).isNull()
        return this
    }

    fun hasCustomTimings(customTimings: Map<String, Long>): ViewEventAssert {
        customTimings.entries.forEach { entry ->
            assertThat(actual.view.customTimings?.additionalProperties)
                .hasEntrySatisfying(entry.key) {
                    assertThat(it).isCloseTo(
                        entry.value,
                        Offset.offset(TimeUnit.MILLISECONDS.toNanos(10))
                    )
                }
        }

        return this
    }

    fun hasUserInfo(expected: UserInfo?): ViewEventAssert {
        assertThat(actual.usr?.id)
            .overridingErrorMessage(
                "Expected event to have usr.id ${expected?.id} " +
                    "but was ${actual.usr?.id}"
            )
            .isEqualTo(expected?.id)
        assertThat(actual.usr?.name)
            .overridingErrorMessage(
                "Expected event to have usr.name ${expected?.name} " +
                    "but was ${actual.usr?.name}"
            )
            .isEqualTo(expected?.name)
        assertThat(actual.usr?.email)
            .overridingErrorMessage(
                "Expected event to have usr.email ${expected?.email} " +
                    "but was ${actual.usr?.email}"
            )
            .isEqualTo(expected?.email)
        assertThat(actual.usr?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have user additional" +
                    " properties ${expected?.additionalProperties} " +
                    "but was ${actual.usr?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.additionalProperties)
        return this
    }

    fun hasUserInfo(expected: ViewEvent.Usr?): ViewEventAssert {
        assertThat(actual.usr?.id)
            .overridingErrorMessage(
                "Expected event to have usr.id ${expected?.id} " +
                    "but was ${actual.usr?.id}"
            )
            .isEqualTo(expected?.id)
        assertThat(actual.usr?.name)
            .overridingErrorMessage(
                "Expected event to have usr.name ${expected?.name} " +
                    "but was ${actual.usr?.name}"
            )
            .isEqualTo(expected?.name)
        assertThat(actual.usr?.email)
            .overridingErrorMessage(
                "Expected event to have usr.email ${expected?.email} " +
                    "but was ${actual.usr?.email}"
            )
            .isEqualTo(expected?.email)
        assertThat(actual.usr?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have user additional " +
                    "properties ${expected?.additionalProperties} " +
                    "but was ${actual.usr?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.additionalProperties)
        return this
    }

    fun hasCpuMetric(expectedTicks: Double?): ViewEventAssert {
        assertThat(actual.view.cpuTicksCount)
            .overridingErrorMessage(
                "Expected event to have view.cpu_ticks_count $expectedTicks " +
                    "but was ${actual.view.cpuTicksCount}"
            )
            .isEqualTo(expectedTicks)

        val expectedTicksPerSeconds = if (expectedTicks != null) {
            (expectedTicks * ONE_SECOND_NS) / actual.view.timeSpent
        } else {
            null
        }
        assertThat(actual.view.cpuTicksPerSecond)
            .overridingErrorMessage(
                "Expected event to have view.cpu_ticks_per_second $expectedTicksPerSeconds " +
                    "but was ${actual.view.cpuTicksPerSecond}"
            )
            .isEqualTo(expectedTicksPerSeconds)
        return this
    }

    fun hasMemoryMetric(average: Double?, max: Double?): ViewEventAssert {
        assertThat(actual.view.memoryAverage)
            .overridingErrorMessage(
                "Expected event to have view.memory_average $average " +
                    "but was ${actual.view.memoryAverage}"
            )
            .isEqualTo(average)
        assertThat(actual.view.memoryMax)
            .overridingErrorMessage(
                "Expected event to have view.memory_max $max " +
                    "but was ${actual.view.memoryMax}"
            )
            .isEqualTo(max)
        return this
    }

    fun hasRefreshRateMetric(average: Double?, min: Double?): ViewEventAssert {
        if (average == null) {
            assertThat(actual.view.refreshRateAverage as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_average $average " +
                        "but was ${actual.view.refreshRateAverage}"
                )
                .isNull()
        } else {
            assertThat(actual.view.refreshRateAverage as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_average $average " +
                        "but was ${actual.view.refreshRateAverage}"
                )
                .isCloseTo(average, Percentage.withPercentage(1.0))
        }
        if (min == null) {
            assertThat(actual.view.refreshRateMin as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_min $min " +
                        "but was ${actual.view.refreshRateMin}"
                )
                .isNull()
        } else {
            assertThat(actual.view.refreshRateMin as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_min $min " +
                        "but was ${actual.view.refreshRateMin}"
                )
                .isCloseTo(min, Percentage.withPercentage(1.0))
        }
        return this
    }

    fun containsExactlyContextAttributes(expected: Map<String, Any?>) {
        assertThat(actual.context?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have context " +
                    "additional properties $expected " +
                    "but was ${actual.context?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected)
    }

    fun hasLiteSessionPlan(): ViewEventAssert {
        assertThat(actual.dd.session?.plan)
            .overridingErrorMessage(
                "Expected event to have a session plan of 1 instead it was %s",
                actual.dd.session?.plan ?: "null"
            )
            .isEqualTo(ViewEvent.Plan.PLAN_1)
        return this
    }

    fun hasSource(source: ViewEvent.Source?): ViewEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event to have a source %s" +
                    " instead it was %s",
                actual.source ?: "null",
                source ?: "null"
            )
            .isEqualTo(source)
        return this
    }

    companion object {

        internal val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1)
        internal const val TIMESTAMP_THRESHOLD_MS = 50L
        internal fun assertThat(actual: ViewEvent): ViewEventAssert =
            ViewEventAssert(actual)
    }
}
