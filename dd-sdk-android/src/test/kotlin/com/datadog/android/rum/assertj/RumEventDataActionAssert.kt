/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.event.RumEventData
import java.util.UUID
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataActionAssert(actual: RumEventData.Action) :
    AbstractObjectAssert<RumEventDataActionAssert, RumEventData.Action>(
        actual,
        RumEventDataActionAssert::class.java
    ) {

    fun hasType(expected: String): RumEventDataActionAssert {
        assertThat(actual.type)
            .overridingErrorMessage(
                "Expected event data to have type $expected but was ${actual.type}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasId(expected: String): RumEventDataActionAssert {
        assertThat(actual.id)
            .overridingErrorMessage(
                "Expected event data to have id $expected but was ${actual.id}"
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

    fun hasDurationGreaterThan(lowerBound: Long): RumEventDataActionAssert {
        assertThat(actual.durationNanoSeconds)
            .overridingErrorMessage(
                "Expected event data to have duration greater than $lowerBound " +
                    "but was ${actual.durationNanoSeconds}"
            )
            .isGreaterThanOrEqualTo(lowerBound)
        return this
    }

    fun hasErrorCount(expected: Int): RumEventDataActionAssert {
        assertThat(actual.errorCount)
            .overridingErrorMessage(
                "Expected event data to have errorCount $expected " +
                    "but was ${actual.errorCount}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResourceCount(expected: Int): RumEventDataActionAssert {
        assertThat(actual.resourceCount)
            .overridingErrorMessage(
                "Expected event data to have resourceCount $expected " +
                    "but was ${actual.resourceCount}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: RumEventData.Action): RumEventDataActionAssert =
            RumEventDataActionAssert(actual)
    }
}
