/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.event.RumEventData
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

    fun hasDurationGreaterThan(upperBound: Long): RumEventDataViewAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration greater than $upperBound " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isGreaterThanOrEqualTo(upperBound)
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

    fun hasErrorCount(expected: Int): RumEventDataViewAssert {
        assertThat(actual.errorCount)
            .overridingErrorMessage(
                "Expected event data to have errorCount $expected " +
                    "but was ${actual.errorCount}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResourceCount(expected: Int): RumEventDataViewAssert {
        assertThat(actual.resourceCount)
            .overridingErrorMessage(
                "Expected event data to have resourceCount $expected " +
                    "but was ${actual.resourceCount}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionCount(expected: Int): RumEventDataViewAssert {
        assertThat(actual.actionCount)
            .overridingErrorMessage(
                "Expected event data to have actionCount $expected " +
                    "but was ${actual.actionCount}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal const val DURATION_THRESHOLD_NANOS = 1000L

        internal fun assertThat(actual: RumEventData.View): RumEventDataViewAssert =
            RumEventDataViewAssert(actual)
    }
}
