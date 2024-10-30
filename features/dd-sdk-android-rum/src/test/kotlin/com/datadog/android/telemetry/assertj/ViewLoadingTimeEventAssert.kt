/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.assertj

import com.datadog.android.telemetry.model.TelemetryUsageEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class ViewLoadingTimeEventAssert(actual: TelemetryUsageEvent.Usage.AddViewLoadingTime) :
    AbstractObjectAssert<ViewLoadingTimeEventAssert, TelemetryUsageEvent.Usage.AddViewLoadingTime>(
        actual,
        ViewLoadingTimeEventAssert::class.java
    ) {

    fun hasNoView(expected: Boolean): ViewLoadingTimeEventAssert {
        assertThat(actual.noView)
            .overridingErrorMessage(
                "Expected event data to have noView $expected but was ${actual.noView}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoActiveView(expected: Boolean): ViewLoadingTimeEventAssert {
        assertThat(actual.noActiveView)
            .overridingErrorMessage(
                "Expected event data to have noActiveView $expected but was ${actual.noActiveView}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOverwritten(expected: Boolean): ViewLoadingTimeEventAssert {
        assertThat(actual.overwritten)
            .overridingErrorMessage(
                "Expected event data to have overwriten $expected but was ${actual.overwritten}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(actual: TelemetryUsageEvent.Usage.AddViewLoadingTime) =
            ViewLoadingTimeEventAssert(actual)
    }
}
