/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.RumEventData
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataViewAssert(actual: RumEventData.View) :
    AbstractObjectAssert<RumEventDataViewAssert, RumEventData.View>(
        actual,
        RumEventDataViewAssert::class.java
    ) {

    fun hasName(expected: String): RumEventDataViewAssert {
        assertThat(actual.name)
            .overridingErrorMessage(
                "Expected event data to have name $expected but was ${actual.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDuration(
        expected: Long,
        offset: Long = DURATION_THRESHOLD_NANOS
    ): RumEventDataViewAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration $expected " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationLowerThan(upperBound: Long): RumEventDataViewAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration lower than $upperBound " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isLessThanOrEqualTo(upperBound)
        return this
    }

    fun hasVersion(expected: Int): RumEventDataViewAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected but was ${actual.version}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMeasures(assert: RumEventDataViewMeasuresAssert.() -> Unit): RumEventDataViewAssert {

        RumEventDataViewMeasuresAssert(actual.measures).assert()

        return this
    }

    companion object {

        internal const val DURATION_THRESHOLD_NANOS = 1000L

        internal fun assertThat(actual: RumEventData.View): RumEventDataViewAssert =
            RumEventDataViewAssert(actual)
    }
}
