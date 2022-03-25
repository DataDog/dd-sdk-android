/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.datadog.android.core.internal.receiver.ThreadSafeReceiver
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.debugWithTelemetry

internal class BroadcastReceiverSystemInfoProvider(
    private val buildSdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider()
) :
    ThreadSafeReceiver(), SystemInfoProvider {

    private var systemInfo: SystemInfo = SystemInfo()

    // region BroadcastReceiver

    override fun onReceive(context: Context, intent: Intent?) {
        when (val action = intent?.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                handleBatteryIntent(intent)
            }
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                handlePowerSaveIntent(context)
            }
            else -> {
                sdkLogger.debugWithTelemetry("Received unknown broadcast intent: [$action]")
            }
        }
    }

    // endregion

    // region SystemInfoProvider

    @SuppressLint("InlinedApi")
    override fun register(context: Context) {
        registerIntentFilter(context, Intent.ACTION_BATTERY_CHANGED)
        if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.LOLLIPOP) {
            registerIntentFilter(context, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
    }

    override fun unregister(context: Context) {
        unregisterReceiver(context)
    }

    override fun getLatestSystemInfo(): SystemInfo {
        return systemInfo
    }

    // endregion

    // region Internal

    private fun registerIntentFilter(context: Context, action: String) {
        val filter = IntentFilter()
        filter.addAction(action)
        registerReceiver(context, filter)?.let { onReceive(context, it) }
    }

    private fun handleBatteryIntent(intent: Intent) {
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
        systemInfo = systemInfo.copy(
            batteryFullOrCharging = batteryFullOrCharging,
            batteryLevel = batteryLevel,
            onExternalPowerSource = onExternalPowerSource
        )
    }

    @SuppressLint("NewApi")
    private fun handlePowerSaveIntent(context: Context) {
        if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val powerSaveMode = powerManager?.isPowerSaveMode ?: false
            systemInfo = systemInfo.copy(
                powerSaveMode = powerSaveMode
            )
        }
    }

    // endregion

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
