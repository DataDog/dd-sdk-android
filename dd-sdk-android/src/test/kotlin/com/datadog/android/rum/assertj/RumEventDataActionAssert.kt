/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.RumEventData
import java.util.UUID
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataActionAssert(actual: RumEventData.UserAction) :
    AbstractObjectAssert<RumEventDataActionAssert, RumEventData.UserAction>(
        actual,
        RumEventDataActionAssert::class.java
    ) {

    fun hasName(expected: String): RumEventDataActionAssert {
        assertThat(actual.name)
            .overridingErrorMessage(
                "Expected event data to have name $expected but was ${actual.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNonDefaultId(): RumEventDataActionAssert {
        assertThat(actual.id)
            .overridingErrorMessage(
                "Expected event data to have non default id but was ${actual.id}"
            )
            .isNotEqualTo(UUID(0L, 0L))
        return this
    }

    fun hasDuration(
        expected: Long,
        offset: Long = RumEventDataResourceAssert.DURATION_THRESHOLD_NANOS
    ): RumEventDataActionAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration $expected " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationLowerThan(upperBound: Long): RumEventDataActionAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration lower than $upperBound " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isLessThanOrEqualTo(upperBound)
        return this
    }

    companion object {

        internal fun assertThat(actual: RumEventData.UserAction): RumEventDataActionAssert =
            RumEventDataActionAssert(actual)
    }
}
