/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions

private typealias TimeseriesApiUsage = InternalTelemetryEvent.ApiUsage.Timeseries

internal class InternalTimeseriesEventAssert(actual: TimeseriesApiUsage) :
    AbstractAssert<InternalTimeseriesEventAssert, TimeseriesApiUsage>(
        actual,
        InternalTimeseriesEventAssert::class.java
    ) {

    fun isEqualTo(expected: TimeseriesApiUsage) {
        hasAdditionalProperties(expected.additionalProperties)
    }

    private fun hasAdditionalProperties(expected: Map<String, Any?>) = apply {
        Assertions.assertThat(actual.additionalProperties)
            .overridingErrorMessage(
                "Expected Timeseries event to have" +
                    " additionalProperties $expected but was ${actual.additionalProperties}"
            )
            .isEqualTo(expected)
    }

    companion object {
        fun assertThat(actual: TimeseriesApiUsage) = InternalTimeseriesEventAssert(actual)
    }
}
