/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
import androidx.annotation.FloatRange
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.domain.InfoProvider
import java.util.concurrent.atomic.AtomicLong

internal class DefaultBatteryInfoProvider(
    private val applicationContext: Context,
    private val timeProvider: TimeProvider,
    private val powerManager: PowerManager? =
        applicationContext.getSystemService(POWER_SERVICE) as? PowerManager,
    private val batteryManager: BatteryManager? = applicationContext.getSystemService(
        BATTERY_SERVICE
    ) as? BatteryManager,
    private val batteryLevelPollInterval: Int = BATTERY_POLL_INTERVAL_MS
) : InfoProvider<BatteryInfo> {

    @Volatile
    @FloatRange(0.0, 1.0)
    private var batteryLevel: Float? = null

    @Volatile
    private var lowPowerMode: Boolean? = null

    private val lastTimeBatteryLevelChecked = AtomicLong(timeProvider.getDeviceElapsedRealtimeMillis())

    private val powerSaveModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isPowerSaveMode = powerManager?.isPowerSaveMode
            isPowerSaveMode?.let {
                lowPowerMode = it
            }
        }
    }

    init {
        registerReceivers()
        buildInitialState()
    }

    @Synchronized
    override fun getState(): BatteryInfo {
        // while we could register a receiver for battery level,
        // it fires far too often (multiple times per second)
        // so it seems better to only poll battery charge state once in a period of time
        val now = timeProvider.getDeviceElapsedRealtimeMillis()
        if (now - batteryLevelPollInterval >= lastTimeBatteryLevelChecked.get()) {
            lastTimeBatteryLevelChecked.set(now)

            resolveBatteryLevel()?.let {
                batteryLevel = it
            }
        }

        // construct current state from the latest values
        return BatteryInfo(
            batteryLevel = batteryLevel,
            lowPowerMode = lowPowerMode
        )
    }

    override fun cleanup() {
        safeUnregisterReceiver(powerSaveModeReceiver)
    }

    private fun safeUnregisterReceiver(receiver: BroadcastReceiver) {
        try {
            applicationContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // ignore - receiver was not previously registered or already unregistered
        }
    }

    private fun buildInitialState() {
        lowPowerMode = resolveLowPowerMode()
        batteryLevel = resolveBatteryLevel()
    }

    private fun registerReceivers() {
        val powerSaveFilter = IntentFilter(ACTION_POWER_SAVE_MODE_CHANGED)
        applicationContext.registerReceiver(powerSaveModeReceiver, powerSaveFilter)
    }

    private fun resolveLowPowerMode(): Boolean? {
        return powerManager?.isPowerSaveMode
    }

    private fun resolveBatteryLevel(): Float? {
        val batteryLevel = batteryManager?.getIntProperty(BATTERY_PROPERTY_CAPACITY)

        return batteryLevel?.let {
            // if there was a problem retrieving the capacity
            val retrievalFailureCode =
                if (applicationContext.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.P) {
                    Integer.MIN_VALUE
                } else {
                    0
                }

            if (it == retrievalFailureCode) return@let null

            normalizeBatteryLevel(it)
        }
    }

    private fun normalizeBatteryLevel(batteryLevel: Int): Float {
        return batteryLevel / FULL_BATTERY_PCT
    }

    private companion object {
        const val FULL_BATTERY_PCT = 100f
        const val BATTERY_POLL_INTERVAL_MS = 60_000 // 60 seconds
    }
}
