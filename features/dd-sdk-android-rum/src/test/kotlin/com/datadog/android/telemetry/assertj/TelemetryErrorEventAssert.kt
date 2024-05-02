/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.assertj

import com.datadog.android.telemetry.model.TelemetryErrorEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class TelemetryErrorEventAssert(actual: TelemetryErrorEvent) :
    AbstractObjectAssert<TelemetryErrorEventAssert, TelemetryErrorEvent>(
        actual,
        TelemetryErrorEventAssert::class.java
    ) {

    fun hasDate(expected: Long): TelemetryErrorEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSource(expected: TelemetryErrorEvent.Source): TelemetryErrorEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event data to have source $expected but was ${actual.source}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasService(expected: String): TelemetryErrorEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected event data to have service $expected but was ${actual.service}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVersion(expected: String): TelemetryErrorEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected but was ${actual.version}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasApplicationId(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.application?.id)
            .overridingErrorMessage(
                "Expected event data to have" +
                    " application.id $expected but was ${actual.application?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.session?.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected but was ${actual.session?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expected but was ${actual.view?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action ID $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMessage(expected: String): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.message)
            .overridingErrorMessage(
                "Expected event data to have telemetry.message $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasErrorStack(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.error?.stack)
            .overridingErrorMessage(
                "Expected event data to have error.stack $expected" +
                    " but was ${actual.telemetry.error?.stack}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasErrorKind(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.error?.kind)
            .overridingErrorMessage(
                "Expected event data to have error.kind $expected" +
                    " but was ${actual.telemetry.error?.kind}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceArchitecture(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.device?.architecture)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device architecture $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceModel(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.device?.model)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device model $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceBrand(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.device?.brand)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device brand $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsBuild(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.os?.build)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os build $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsName(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.os?.name)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os name $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasOsVersion(expected: String?): TelemetryErrorEventAssert {
        assertThat(actual.telemetry.os?.version)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os version $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(actual: TelemetryErrorEvent) = TelemetryErrorEventAssert(actual)
    }
}
