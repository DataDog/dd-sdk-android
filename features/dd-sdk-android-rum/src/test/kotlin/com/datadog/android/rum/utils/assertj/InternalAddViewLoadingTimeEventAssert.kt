/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class InternalAddViewLoadingTimeEventAssert(actual: InternalTelemetryEvent.ApiUsage.AddViewLoadingTime) :
    AbstractAssert<InternalAddViewLoadingTimeEventAssert, InternalTelemetryEvent.ApiUsage.AddViewLoadingTime>(
        actual,
        InternalAddViewLoadingTimeEventAssert::class.java
    ) {

    fun isEqualTo(expected: InternalTelemetryEvent.ApiUsage.AddViewLoadingTime) {
        hasNoView(expected.noView)
        hasNoActiveView(expected.noActiveView)
        hasOverwrite(expected.overwrite)
        hasAdditionalProperties(expected.additionalProperties)
    }

    fun hasNoView(expected: Boolean): InternalAddViewLoadingTimeEventAssert {
        assertThat(actual.noView)
            .overridingErrorMessage(
                "Expected viewLoadingTimeTelemetryEvent event to" +
                    " have noView $expected but was ${actual.noView}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoActiveView(expected: Boolean): InternalAddViewLoadingTimeEventAssert {
        assertThat(actual.noActiveView)
            .overridingErrorMessage(
                "Expected viewLoadingTimeTelemetryEvent event to have" +
                    " noActiveView $expected but was ${actual.noActiveView}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOverwrite(expected: Boolean): InternalAddViewLoadingTimeEventAssert {
        assertThat(actual.overwrite)
            .overridingErrorMessage(
                "Expected viewLoadingTimeTelemetryEvent event to have" +
                    " overwrite $expected but was ${actual.overwrite}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasAdditionalProperties(expected: Map<String, Any?>): InternalAddViewLoadingTimeEventAssert {
        assertThat(actual.additionalProperties)
            .overridingErrorMessage(
                "Expected viewLoadingTimeTelemetryEvent event to have" +
                    " additionalProperties $expected but was ${actual.additionalProperties}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(
            actual: InternalTelemetryEvent.ApiUsage.AddViewLoadingTime
        ): InternalAddViewLoadingTimeEventAssert {
            return InternalAddViewLoadingTimeEventAssert(actual)
        }
    }
}
