/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.assertj

import com.datadog.android.profiling.model.RumMetadataEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumMetadataEventsAssert(actual: RumMetadataEvent) :
    AbstractObjectAssert<RumMetadataEventsAssert, RumMetadataEvent>(
        actual,
        RumMetadataEventsAssert::class.java
    ) {

    fun hasId(expected: String): RumMetadataEventsAssert {
        assertThat(actual.id)
            .overridingErrorMessage(
                "Expected RUM metadata event to have ID $expected " +
                    "but was ${actual.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasName(expected: String?): RumMetadataEventsAssert {
        assertThat(actual.name)
            .overridingErrorMessage(
                "Expected RUM metadata event to have name $expected " +
                    "but was ${actual.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasType(expected: RumMetadataEvent.Type): RumMetadataEventsAssert {
        assertThat(actual.type)
            .overridingErrorMessage(
                "Expected RUM metadata event to have type $expected " +
                    "but was ${actual.type}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasStartNs(expected: Long): RumMetadataEventsAssert {
        assertThat(actual.startNs)
            .overridingErrorMessage(
                "Expected RUM metadata event to have start_ns $expected " +
                    "but was ${actual.startNs}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationNs(expected: Long?): RumMetadataEventsAssert {
        assertThat(actual.durationNs)
            .overridingErrorMessage(
                "Expected RUM metadata event to have duration_ns $expected " +
                    "but was ${actual.durationNs}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        internal fun assertThat(actual: RumMetadataEvent) = RumMetadataEventsAssert(actual)
    }
}
