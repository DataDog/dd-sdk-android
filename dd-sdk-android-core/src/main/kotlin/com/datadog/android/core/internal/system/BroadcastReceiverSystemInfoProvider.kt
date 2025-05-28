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
import android.os.PowerManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.receiver.ThreadSafeReceiver
import kotlin.math.roundToInt

internal class BroadcastReceiverSystemInfoProvider(
    private val internalLogger: InternalLogger
) :
    ThreadSafeReceiver(), SystemInfoProvider {

    private var systemInfo: SystemInfo = SystemInfo()

    // region BroadcastReceiver

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            when (val action = intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    handleBatteryIntent(intent)
                }

                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    handlePowerSaveIntent(context)
                }

                else -> {
                    internalLogger.log(
                        InternalLogger.Level.DEBUG,
                        listOf(
                            InternalLogger.Target.MAINTAINER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        { "Received unknown broadcast intent: [$action]" }
                    )
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { ERROR_HANDLING_BROADCAST_INTENT },
                throwable = e
            )
        }
    }

    // endregion

    // region SystemInfoProvider

    @SuppressLint("InlinedApi")
    override fun register(context: Context) {
        registerIntentFilter(context, Intent.ACTION_BATTERY_CHANGED)
        registerIntentFilter(context, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
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
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, BATTERY_LEVEL_UNKNOWN)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, DEFAULT_BATTERY_SCALE)
        val pluggedStatus = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, BATTERY_UNPLUGGED)
        val batteryStatus = SystemInfo.BatteryStatus.fromAndroidStatus(status)
        val batteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)

        @Suppress("UnsafeThirdPartyFunctionCall") // Not a NaN here
        val batteryLevel = ((level * DEFAULT_BATTERY_SCALE.toFloat()) / scale).roundToInt()
        val onExternalPowerSource = pluggedStatus in PLUGGED_IN_STATUS_VALUES || !batteryPresent
        val batteryFullOrCharging = batteryStatus in batteryFullOrChargingStatus
        systemInfo = systemInfo.copy(
            batteryFullOrCharging = batteryFullOrCharging,
            batteryLevel = batteryLevel,
            onExternalPowerSource = onExternalPowerSource
        )
    }

    private fun handlePowerSaveIntent(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSaveMode = powerManager?.isPowerSaveMode ?: false
        systemInfo = systemInfo.copy(
            powerSaveMode = powerSaveMode
        )
    }

    // endregion

    companion object {

        private const val DEFAULT_BATTERY_SCALE = 100
        private const val BATTERY_UNPLUGGED = -1
        private const val BATTERY_LEVEL_UNKNOWN = -1
        private const val ERROR_HANDLING_BROADCAST_INTENT = "Error handling system info broadcast intent."

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
