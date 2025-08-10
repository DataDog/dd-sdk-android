/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Context.POWER_SERVICE
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import android.os.PowerManager
import kotlin.math.roundToInt

internal class DefaultBatteryInfoProvider(
    private val applicationContext: Context,
    private val powerManager: PowerManager? = applicationContext.getSystemService(POWER_SERVICE) as? PowerManager,
    private val batteryManager: BatteryManager? = applicationContext.getSystemService(
        BATTERY_SERVICE
    ) as? BatteryManager
) : BatteryInfoProvider {
    override fun getBatteryState(): BatteryInfo {
        return BatteryInfo(
            lowPowerMode = getLowPowerMode(),
            batteryLevel = getBatteryLevel()
        )
    }

    private fun getLowPowerMode(): Boolean? {
        return powerManager?.isPowerSaveMode
    }

    private fun getBatteryLevel(): Float? {
        val rawCapacity = batteryManager?.getIntProperty(BATTERY_PROPERTY_CAPACITY) ?: return null
        val rawBatteryLevel = rawCapacity / FULL_BATTERY_PCT
        return roundToOneDecimalPlace(rawBatteryLevel)
    }

    private fun roundToOneDecimalPlace(input: Float): Float {
        return (input * DECIMAL_SCALING).roundToInt() / DECIMAL_SCALING
    }

    private companion object {
        const val FULL_BATTERY_PCT = 100f
        const val DECIMAL_SCALING = 10f
    }
}
