/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.model.VitalEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class VitalAppLaunchPropertiesAssert(actual: VitalEvent.Vital.AppLaunchProperties) :
    AbstractObjectAssert<VitalAppLaunchPropertiesAssert, VitalEvent.Vital.AppLaunchProperties>(
        actual,
        VitalAppLaunchPropertiesAssert::class.java
    ) {

    fun hasName(expected: String?) = apply {
        assertThat(actual.name).overridingErrorMessage(
            "Expected event data to have name $expected but was ${actual.name}"
        ).isEqualTo(expected)
    }

    fun hasDescription(expected: String?) = apply {
        assertThat(actual.description).overridingErrorMessage(
            "Expected event data to have description $expected but was ${actual.description}"
        ).isEqualTo(expected)
    }

    fun hasAppLaunchMetric(expected: VitalEvent.AppLaunchMetric) = apply {
        assertThat(actual.appLaunchMetric).overridingErrorMessage(
            "Expected event data to have appLaunchMetric $expected but was ${actual.appLaunchMetric}"
        ).isEqualTo(expected)
    }

    fun hasDuration(expected: Number) = apply {
        assertThat(actual.duration).overridingErrorMessage(
            "Expected event data to have duration $expected but was ${actual.duration}"
        ).isEqualTo(expected)
    }

    fun hasStartupType(expected: VitalEvent.StartupType?) = apply {
        assertThat(actual.startupType).overridingErrorMessage(
            "Expected event data to have startupType $expected but was ${actual.startupType}"
        ).isEqualTo(expected)
    }

    fun hasPrewarmed(expected: Boolean?) = apply {
        assertThat(actual.isPrewarmed).overridingErrorMessage(
            "Expected event data to have isPrewarmed $expected but was ${actual.isPrewarmed}"
        ).isEqualTo(expected)
    }

    fun hasSavedInstanceStateBundle(expected: Boolean?) = apply {
        assertThat(actual.hasSavedInstanceStateBundle).overridingErrorMessage(
            "Expected event data to have hasSavedInstanceStateBundle " +
                "$expected but was ${actual.hasSavedInstanceStateBundle}"
        ).isEqualTo(expected)
    }

    companion object {
        internal fun assertThat(
            actual: VitalEvent.Vital.AppLaunchProperties
        ): VitalAppLaunchPropertiesAssert = VitalAppLaunchPropertiesAssert(actual)
    }
}
