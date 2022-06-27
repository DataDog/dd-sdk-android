/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.content.Intent
import android.os.BatteryManager
import com.datadog.android.plugin.BatteryIntentHandler
import com.datadog.android.plugin.BatteryIntentResults

internal class DefaultBatteryIntentHandler: BatteryIntentHandler {
    override fun handleBatteryIntent(intent: Intent): BatteryIntentResults {
        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pluggedStatus = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val batteryStatus = SystemInfo.BatteryStatus.fromAndroidStatus(status)
        val batteryLevel = (level * 100) / scale
        val onExternalPowerSource = pluggedStatus in PLUGGED_IN_STATUS_VALUES
        val batteryFullOrCharging = batteryStatus in batteryFullOrChargingStatus


        return BatteryIntentResults(batteryFullOrCharging, batteryLevel, onExternalPowerSource)
    }

    companion object {

        private val batteryFullOrChargingStatus = setOf(
            SystemInfo.BatteryStatus.CHARGING,
            SystemInfo.BatteryStatus.FULL
        )

        private val PLUGGED_IN_STATUS_VALUES = setOf(
            BatteryManager.BATTERY_PLUGGED_AC,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_PLUGGED_USB
        )
    }
}