/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.os.BatteryManager

internal data class SystemInfo(
    val batteryFullOrCharging: Boolean = false,
    val batteryLevel: Int = -1,
    val powerSaveMode: Boolean = false,
    val onExternalPowerSource: Boolean = false
) {

    internal enum class BatteryStatus {
        UNKNOWN,
        CHARGING,
        DISCHARGING,
        NOT_CHARGING,
        FULL;

        companion object {

            fun fromAndroidStatus(status: Int): BatteryStatus {
                return when (status) {
                    BatteryManager.BATTERY_STATUS_FULL -> FULL
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> NOT_CHARGING
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> DISCHARGING
                    BatteryManager.BATTERY_STATUS_CHARGING -> CHARGING
                    else -> UNKNOWN
                }
            }
        }
    }
}
