/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import androidx.annotation.FloatRange

/**
 * Provides information about the battery state.
 *
 * @property batteryLevel the current battery charge level, expressed as a float from 0.0f (empty) to 1.0f (full).
 * @property lowPowerMode a boolean indicating whether the device is currently in Low Power Mode.
 */
internal data class BatteryInfo(
    @FloatRange(0.0, 1.0) val batteryLevel: Float? = null,
    val lowPowerMode: Boolean? = null
) {
    fun toMap(): Map<String, Any> = buildMap {
        batteryLevel?.let { put(BATTERY_LEVEL_KEY, it) }
        lowPowerMode?.let { put(LOW_POWER_MODE_KEY, it) }
    }

    internal companion object {
        const val BATTERY_LEVEL_KEY = "battery_level"
        const val LOW_POWER_MODE_KEY = "low_power_mode"

        fun fromMap(map: Map<String, Any>): BatteryInfo {
            return BatteryInfo(
                batteryLevel = map[BATTERY_LEVEL_KEY] as? Float,
                lowPowerMode = map[LOW_POWER_MODE_KEY] as? Boolean
            )
        }
    }
}
