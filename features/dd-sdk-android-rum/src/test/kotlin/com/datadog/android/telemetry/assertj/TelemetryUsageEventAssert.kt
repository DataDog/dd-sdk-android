/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class TelemetryUsageEventAssert(actual: TelemetryUsageEvent) :
    AbstractObjectAssert<TelemetryUsageEventAssert, TelemetryUsageEvent>(
        actual,
        TelemetryUsageEventAssert::class.java
    ) {

    fun hasDate(expected: Long): TelemetryUsageEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSource(expected: TelemetryUsageEvent.Source): TelemetryUsageEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event data to have source $expected but was ${actual.source}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasService(expected: String): TelemetryUsageEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected event data to have service $expected but was ${actual.service}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVersion(expected: String): TelemetryUsageEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected but was ${actual.version}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasApplicationId(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.application?.id)
            .overridingErrorMessage(
                "Expected event data to have" +
                    " application.id $expected but was ${actual.application?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.session?.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected but was ${actual.session?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expected but was ${actual.view?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasAdditionalProperties(additionalProperties: Map<String, Any?>): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.additionalProperties)
            .overridingErrorMessage(
                "Expected event data to have telemetry.additionalProperties $additionalProperties" +
                    " but was ${actual.telemetry.additionalProperties}"
            )
            .isEqualTo(additionalProperties)
        return this
    }

    fun hasDeviceArchitecture(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.device?.architecture)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device architecture $expected" +
                    " but was ${actual.telemetry.device?.architecture}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceModel(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.device?.model)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device model $expected" +
                    " but was ${actual.telemetry.device?.model}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceBrand(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.device?.brand)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device brand $expected" +
                    " but was ${actual.telemetry.device?.brand}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsBuild(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.os?.build)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os build $expected" +
                    " but was ${actual.telemetry.os?.build}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsName(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.os?.name)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os name $expected" +
                    " but was ${actual.telemetry.os?.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsVersion(expected: String?): TelemetryUsageEventAssert {
        assertThat(actual.telemetry.os?.version)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os version $expected" +
                    " but was ${actual.telemetry.os?.version}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUsage(expected: InternalTelemetryEvent.ApiUsage) {
        when (expected) {
            is InternalTelemetryEvent.ApiUsage.AddViewLoadingTime -> {
                val actualUsage = actual.telemetry.usage as TelemetryUsageEvent.Usage.AddViewLoadingTime
                assertThat(actualUsage)
                    .hasNoView(expected.noView)
                    .hasOverwritten(actualUsage.overwritten)
                    .hasNoActiveView(expected.noActiveView)
            }

            is InternalTelemetryEvent.ApiUsage.AddOperationStepVital -> {
                val actualUsage = actual.telemetry.usage as TelemetryUsageEvent.Usage.AddOperationStepVital
                assertThat(actualUsage)
                    .hasActionType(expected.actionType)
            }

            is InternalTelemetryEvent.ApiUsage.TrackWebView -> {
                actual.telemetry.usage as TelemetryUsageEvent.Usage.TrackWebView
            }
        }
    }

    private class OperationStepVitalEventAssert(actual: TelemetryUsageEvent.Usage.AddOperationStepVital) :
        AbstractObjectAssert<OperationStepVitalEventAssert, TelemetryUsageEvent.Usage.AddOperationStepVital>(
            actual,
            OperationStepVitalEventAssert::class.java
        ) {
        fun hasActionType(expected: InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType) = apply {
            assertThat(actual.actionType.name)
                .overridingErrorMessage(
                    "Expected operationStepVitalUsage event to have actionType $expected " +
                        "but was ${actual.actionType.name}"
                )
                .isEqualTo(expected.name)
        }
    }

    private class ViewLoadingTimeEventAssert(actual: TelemetryUsageEvent.Usage.AddViewLoadingTime) :
        AbstractObjectAssert<ViewLoadingTimeEventAssert, TelemetryUsageEvent.Usage.AddViewLoadingTime>(
            actual,
            ViewLoadingTimeEventAssert::class.java
        ) {

        fun hasNoView(expected: Boolean) = apply {
            assertThat(actual.noView)
                .overridingErrorMessage(
                    "Expected viewLoadingTimeUsage event to" +
                        " have noView $expected but was ${actual.noView}"
                )
                .isEqualTo(expected)
        }

        fun hasNoActiveView(expected: Boolean) = apply {
            assertThat(actual.noActiveView)
                .overridingErrorMessage(
                    "Expected viewLoadingTimeUsage event to have" +
                        " noActiveView $expected but was ${actual.noActiveView}"
                )
                .isEqualTo(expected)
        }

        fun hasOverwritten(expected: Boolean) = apply {
            assertThat(actual.overwritten)
                .overridingErrorMessage(
                    "Expected viewLoadingTimeUsage event to have" +
                        " overwritten $expected but was ${actual.overwritten}"
                )
                .isEqualTo(expected)
        }
    }

    companion object {
        fun assertThat(actual: TelemetryUsageEvent) = TelemetryUsageEventAssert(actual)

        private fun assertThat(actual: TelemetryUsageEvent.Usage.AddViewLoadingTime) =
            ViewLoadingTimeEventAssert(actual)

        private fun assertThat(actual: TelemetryUsageEvent.Usage.AddOperationStepVital) =
            OperationStepVitalEventAssert(actual)
    }
}
