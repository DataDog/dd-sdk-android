/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.datadog.android.core.internal.utils.sdkLogger

internal class BroadcastReceiverSystemInfoProvider :
    BroadcastReceiver(), SystemInfoProvider {

    private var systemInfo: SystemInfo = SystemInfo()

    internal fun register(context: Context) {
        registerIntentFilter(context, Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            registerIntentFilter(context, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
    }

    // region BroadcastReceiver

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        when (action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                sdkLogger.d("$TAG: received battery update")
                handleBatteryIntent(intent)
            }
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                sdkLogger.d("$TAG: received power save mode update")
                handlePowerSaveIntent(context)
            }
            else -> sdkLogger.d("$TAG: received unknown update $action")
        }
    }

    // endregion

    // region SystemInfoProvider

    override fun getLatestSystemInfo(): SystemInfo {
        return systemInfo
    }

    // endregion

    // region Internal

    private fun registerIntentFilter(context: Context, action: String) {
        val filter = IntentFilter()
        filter.addAction(action)
        val intent = context.registerReceiver(this, filter)
        onReceive(context, intent)
    }

    private fun handleBatteryIntent(intent: Intent) {
        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)

        systemInfo = systemInfo.copy(
            batteryStatus = SystemInfo.BatteryStatus.fromAndroidStatus(status),
            batteryLevel = (level * 100) / scale
        )
    }

    private fun handlePowerSaveIntent(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val powerSaveMode = powerManager?.isPowerSaveMode ?: false
            systemInfo = systemInfo.copy(
                powerSaveMode = powerSaveMode
            )
        }
    }

    // endregion

    companion object {
        private const val TAG = "BroadcastReceiver"
    }
}
