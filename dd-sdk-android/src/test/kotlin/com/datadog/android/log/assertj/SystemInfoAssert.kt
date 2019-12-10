/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.datadog.android.log.internal.system.SystemInfo
import kotlin.math.max
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class SystemInfoAssert(actual: SystemInfo) :
    AbstractObjectAssert<SystemInfoAssert, SystemInfo>(actual, SystemInfoAssert::class.java) {

    fun hasBatteryStatus(expected: SystemInfo.BatteryStatus): SystemInfoAssert {
        assertThat(actual.batteryStatus)
            .overridingErrorMessage(
                "Expected systemInfo to have batteryStatus $expected " +
                    "but was ${actual.batteryStatus}"
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

    companion object {

        internal fun assertThat(actual: SystemInfo): SystemInfoAssert =
            SystemInfoAssert(actual)
    }
}
