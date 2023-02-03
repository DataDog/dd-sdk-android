/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.core.internal.system.SystemInfo
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import kotlin.math.max

internal class SystemInfoAssert(actual: SystemInfo) :
    AbstractObjectAssert<SystemInfoAssert, SystemInfo>(actual, SystemInfoAssert::class.java) {

    fun hasPowerSaveMode(expected: Boolean): SystemInfoAssert {
        assertThat(actual.powerSaveMode)
            .overridingErrorMessage(
                "Expected systemInfo to have powerSaveMode $expected " +
                    "but was ${actual.powerSaveMode}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasBatteryLevel(expected: Int, scale: Int = 100): SystemInfoAssert {
        assertThat(actual.batteryLevel)
            .overridingErrorMessage(
                "Expected systemInfo to have batteryLevel $expected " +
                    "but was ${actual.batteryLevel}"
            )
            .isCloseTo(expected, Offset.offset(max(100 / scale, 1)))

        return this
    }

    fun hasBatteryFullOrCharging(expected: Boolean): SystemInfoAssert {
        assertThat(actual.batteryFullOrCharging).overridingErrorMessage(
            "Expected systemInfo to have batteryFullOrCharging flag $expected " +
                "but was ${actual.batteryFullOrCharging}"
        ).isEqualTo(expected)
        return this
    }

    fun hasOnExternalPowerSource(expected: Boolean): SystemInfoAssert {
        assertThat(actual.onExternalPowerSource).overridingErrorMessage(
            "Expected systemInfo to have onExternalPowerSource flag $expected " +
                "but was ${actual.onExternalPowerSource}"
        ).isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: SystemInfo): SystemInfoAssert =
            SystemInfoAssert(actual)
    }
}
