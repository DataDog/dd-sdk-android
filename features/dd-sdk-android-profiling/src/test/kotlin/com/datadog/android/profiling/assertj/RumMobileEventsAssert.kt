/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.assertj

import com.datadog.android.profiling.model.RumMobileEvents
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumMobileEventsAssert(actual: RumMobileEvents) :
    AbstractObjectAssert<RumMobileEventsAssert, RumMobileEvents>(
        actual,
        RumMobileEventsAssert::class.java
    ) {

    fun hasErrors(expected: List<RumMobileEvents.Error>?): RumMobileEventsAssert {
        assertThat(actual.errors)
            .overridingErrorMessage(
                "Expected rum mobile events to have errors $expected " +
                    "but was ${actual.errors}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLongTasks(expected: List<RumMobileEvents.LongTask>?): RumMobileEventsAssert {
        assertThat(actual.longTasks)
            .overridingErrorMessage(
                "Expected rum mobile events to have longTasks $expected " +
                    "but was ${actual.longTasks}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        internal fun assertThat(actual: RumMobileEvents) = RumMobileEventsAssert(actual)
    }
}
