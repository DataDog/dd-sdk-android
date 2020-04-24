/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.RumEventData
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataResourceAssert(actual: RumEventData.Resource) :
    AbstractObjectAssert<RumEventDataResourceAssert, RumEventData.Resource>(
        actual,
        RumEventDataResourceAssert::class.java
    ) {

    fun hasKind(expected: RumResourceKind): RumEventDataResourceAssert {
        assertThat(actual.kind)
            .overridingErrorMessage(
                "Expected event data to have kind $expected but was ${actual.kind}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUrl(expected: String): RumEventDataResourceAssert {
        assertThat(actual.url)
            .overridingErrorMessage(
                "Expected event data to have url $expected but was ${actual.url}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMethod(expected: String): RumEventDataResourceAssert {
        assertThat(actual.method)
            .overridingErrorMessage(
                "Expected event data to have method $expected but was ${actual.method}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDuration(
        expected: Long,
        offset: Long = DURATION_THRESHOLD_NANOS
    ): RumEventDataResourceAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration $expected " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationLowerThan(upperBound: Long): RumEventDataResourceAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration lower than $upperBound " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isLessThanOrEqualTo(upperBound)
        return this
    }

    fun hasDurationGreaterThan(upperBound: Long): RumEventDataResourceAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration greater than $upperBound " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isGreaterThanOrEqualTo(upperBound)
        return this
    }

    companion object {

        internal const val DURATION_THRESHOLD_NANOS = 1000L

        internal fun assertThat(actual: RumEventData.View): RumEventDataViewAssert =
            RumEventDataViewAssert(actual)
    }
}
