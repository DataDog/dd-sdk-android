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

    fun hasDate(expected: Long) = apply {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
    }

    fun hasSource(expected: TelemetryDebugEvent.Source) = apply {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event data to have source $expected but was ${actual.source}"
            )
            .isEqualTo(expected)
    }

    fun hasService(expected: String) = apply {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected event data to have service $expected but was ${actual.service}"
            )
            .isEqualTo(expected)
    }

    fun hasVersion(expected: String) = apply {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected but was ${actual.version}"
            )
            .isEqualTo(expected)
    }

    fun hasApplicationId(expected: String?) = apply {
        assertThat(actual.application?.id)
            .overridingErrorMessage(
                "Expected event data to have" +
                    " application.id $expected but was ${actual.application?.id}"
            )
            .isEqualTo(expected)
    }

    fun hasSessionId(expected: String?) = apply {
        assertThat(actual.session?.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected but was ${actual.session?.id}"
            )
            .isEqualTo(expected)
    }

    fun hasViewId(expected: String?) = apply {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expected but was ${actual.view?.id}"
            )
            .isEqualTo(expected)
    }

    fun hasActionId(expected: String?) = apply {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
    }

    fun hasMessage(expected: String) = apply {
        assertThat(actual.telemetry.message)
            .overridingErrorMessage(
                "Expected event data to have telemetry.message $expected" +
                    " but was ${actual.telemetry.message}"
            )
            .isEqualTo(expected)
    }

    fun hasAdditionalProperties(additionalProperties: Map<String, Any?>) = apply {
        assertThat(actual.telemetry.additionalProperties)
            .overridingErrorMessage(
                "Expected event data to have telemetry.additionalProperties $additionalProperties" +
                    " but was ${actual.telemetry.additionalProperties}"
            )
            .isEqualTo(additionalProperties)
    }

    fun hasDeviceArchitecture(expected: String?) = apply {
        assertThat(actual.telemetry.device?.architecture)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device architecture $expected" +
                    " but was ${actual.telemetry.device?.architecture}"
            )
            .isEqualTo(expected)
    }

    fun hasDeviceModel(expected: String?) = apply {
        assertThat(actual.telemetry.device?.model)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device model $expected" +
                    " but was ${actual.telemetry.device?.model}"
            )
            .isEqualTo(expected)
    }

    fun hasDeviceBrand(expected: String?) = apply {
        assertThat(actual.telemetry.device?.brand)
            .overridingErrorMessage(
                "Expected event data to have telemetry.device brand $expected" +
                    " but was ${actual.telemetry.device?.brand}"
            )
            .isEqualTo(expected)
    }

    fun hasOsBuild(expected: String?) = apply {
        assertThat(actual.telemetry.os?.build)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os build $expected" +
                    " but was ${actual.telemetry.os?.build}"
            )
            .isEqualTo(expected)
    }

    fun hasOsName(expected: String?) = apply {
        assertThat(actual.telemetry.os?.name)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os name $expected" +
                    " but was ${actual.telemetry.os?.name}"
            )
            .isEqualTo(expected)
    }

    fun hasOsVersion(expected: String?) = apply {
        assertThat(actual.telemetry.os?.version)
            .overridingErrorMessage(
                "Expected event data to have telemetry.os version $expected" +
                    " but was ${actual.telemetry.os?.version}"
            )
            .isEqualTo(expected)
    }

    companion object {
        fun assertThat(actual: TelemetryDebugEvent) = TelemetryDebugEventAssert(actual)
    }
}
