/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.model.ViewEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ViewEventAssert(actual: ViewEvent) :
    AbstractObjectAssert<ViewEventAssert, ViewEvent>(
        actual,
        ViewEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = RumEventAssert.TIMESTAMP_THRESHOLD_MS
    ): ViewEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasName(expected: String): ViewEventAssert {
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expected but was ${actual.view.url}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDuration(
        expected: Long,
        offset: Long = DURATION_THRESHOLD_NANOS
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

    fun hasUserActionCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.action.count)
            .overridingErrorMessage(
                "Expected event data to have view.action.count $expected " +
                    "but was ${actual.view.action.count}"
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

    companion object {

        internal const val DURATION_THRESHOLD_NANOS = 1000L

        internal fun assertThat(actual: ViewEvent): ViewEventAssert =
            ViewEventAssert(actual)
    }
}
