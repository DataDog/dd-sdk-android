/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.datadog.android.log.internal.utils.sdkLogger

internal class BroadcastReceiverSystemInfoProvider :
    BroadcastReceiver(), SystemInfoProvider {

    private var systemInfo: SystemInfo = SystemInfo()

    internal fun register(context: Context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val firstIntent = context.registerReceiver(this, filter)
        onReceive(context, firstIntent)
    }

    // region BroadcastReceiver

    override fun onReceive(context: Context, intent: Intent?) {
        sdkLogger.d("$TAG: received network update")
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
            handleBatteryIntent(intent)
        }
    }

    // endregion

    // region SystemInfoProvider

    override fun getLatestSystemInfo(): SystemInfo {
        return systemInfo
    }

    // endregion

    // region Internal

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

    // endregion

    companion object {
        private const val TAG = "BroadcastReceiver"
    }
}
