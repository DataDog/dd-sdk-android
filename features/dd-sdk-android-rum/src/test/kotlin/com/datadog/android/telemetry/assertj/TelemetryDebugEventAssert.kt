/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.assertj

import com.datadog.android.telemetry.model.TelemetryDebugEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class TelemetryDebugEventAssert(actual: TelemetryDebugEvent) :
    AbstractObjectAssert<TelemetryDebugEventAssert, TelemetryDebugEvent>(
        actual,
        TelemetryDebugEventAssert::class.java
    ) {

    fun hasDate(expected: Long): TelemetryDebugEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSource(expected: TelemetryDebugEvent.Source): TelemetryDebugEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event data to have source $expected but was ${actual.source}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasService(expected: String): TelemetryDebugEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected event data to have service $expected but was ${actual.service}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVersion(expected: String): TelemetryDebugEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected but was ${actual.version}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasApplicationId(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.application?.id)
            .overridingErrorMessage(
                "Expected event data to have" +
                    " application.id $expected but was ${actual.application?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.session?.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected but was ${actual.session?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expected but was ${actual.view?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMessage(expected: String): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.message)
            .overridingErrorMessage(
                "Expected event data to have telemetry.message $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasAdditionalProperties(additionalProperties: Map<String, Any?>): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.additionalProperties)
            .overridingErrorMessage(
                "Expected event data to have telemetry.additionalProperties $additionalProperties" +
                    " but was ${actual.telemetry.additionalProperties}"
            )
            .isEqualTo(additionalProperties)
        return this
    }

    fun hasDeviceArchitecture(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.device?.architecture)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device architecture $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceModel(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.device?.model)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device model $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceBrand(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.device?.brand)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device brand $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsBuild(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.os?.build)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os build $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsName(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.os?.name)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os name $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsVersion(expected: String?): TelemetryDebugEventAssert {
        assertThat(actual.telemetry.os?.version)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os version $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(actual: TelemetryDebugEvent) = TelemetryDebugEventAssert(actual)
    }
}
