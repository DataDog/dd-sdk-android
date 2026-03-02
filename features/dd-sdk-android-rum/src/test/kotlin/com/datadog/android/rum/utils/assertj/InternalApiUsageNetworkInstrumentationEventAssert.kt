/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class InternalApiUsageNetworkInstrumentationEventAssert(
    actual: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation
) : AbstractAssert<
    InternalApiUsageNetworkInstrumentationEventAssert,
    InternalTelemetryEvent.ApiUsage.NetworkInstrumentation
    >(
    actual,
    InternalApiUsageNetworkInstrumentationEventAssert::class.java
) {

    fun isEqualTo(expected: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation) {
        hasType(expected.type)
        hasAdditionalProperties(expected.additionalProperties)
    }

    private fun hasType(expected: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType) = apply {
        assertThat(actual.type)
            .overridingErrorMessage(
                "Expected NetworkInstrumentation event to have" +
                    " type $expected but was ${actual.type}"
            )
            .isEqualTo(expected)
    }

    private fun hasAdditionalProperties(expected: Map<String, Any?>) = apply {
        assertThat(actual.additionalProperties)
            .overridingErrorMessage(
                "Expected NetworkInstrumentation event to have" +
                    " additionalProperties $expected but was ${actual.additionalProperties}"
            )
            .isEqualTo(expected)
    }

    companion object {
        fun assertThat(actual: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation) =
            InternalApiUsageNetworkInstrumentationEventAssert(actual)
    }
}
