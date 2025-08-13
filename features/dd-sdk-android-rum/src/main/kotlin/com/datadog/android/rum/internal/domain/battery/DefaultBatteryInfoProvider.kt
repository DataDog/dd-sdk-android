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
import com.datadog.android.rum.internal.domain.InfoProvider
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

internal class DefaultBatteryInfoProvider(
    private val applicationContext: Context,
    private val powerManager: PowerManager? =
        applicationContext.getSystemService(POWER_SERVICE) as? PowerManager,
    private val batteryManager: BatteryManager? = applicationContext.getSystemService(
        BATTERY_SERVICE
    ) as? BatteryManager,
    private val batteryLevelPollInterval: Int = BATTERY_POLL_INTERVAL_MS,
    private val systemClockWrapper: SystemClockWrapper = SystemClockWrapper() // this wrapper is needed for unit tests
) : InfoProvider {

    @Volatile
    private var currentState = BatteryInfo()

    private var lastTimeBatteryLevelChecked = AtomicLong(systemClockWrapper.elapsedRealTime())

    private val powerSaveModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isPowerSaveMode = powerManager?.isPowerSaveMode
            isPowerSaveMode?.let {
                currentState = currentState.copy(
                    lowPowerMode = isPowerSaveMode
                )
            }
        }
    }

    init {
        registerReceivers()
        buildInitialState()
    }

    @Synchronized
    override fun getState(): Map<String, Any> {
        // while we could register a receiver for battery level,
        // it fires far too often (multiple times per second)
        // so it seems better to only poll battery charge state once in a period of time
        val now = systemClockWrapper.elapsedRealTime()
        if (now - batteryLevelPollInterval >= lastTimeBatteryLevelChecked.get()) {
            lastTimeBatteryLevelChecked.set(now)

            getBatteryLevel()?.let {
                currentState = currentState.copy(
                    batteryLevel = it
                )
            }
        }

        return currentState.toMap()
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
        currentState = BatteryInfo(
            lowPowerMode = getLowPowerMode(),
            batteryLevel = getBatteryLevel()
        )
    }

    private fun registerReceivers() {
        val powerSaveFilter = IntentFilter(ACTION_POWER_SAVE_MODE_CHANGED)
        applicationContext.registerReceiver(powerSaveModeReceiver, powerSaveFilter)
    }

    private fun getLowPowerMode(): Boolean? {
        return powerManager?.isPowerSaveMode
    }

    private fun getBatteryLevel(): Float? {
        val batteryLevel = batteryManager?.getIntProperty(BATTERY_PROPERTY_CAPACITY)

        return batteryLevel?.let {
            // if there was a problem retrieving the capacity
            val retrievalFailureCode = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                Integer.MIN_VALUE
            } else {
                0
            }

            if (it == retrievalFailureCode) return@let null

            normalizeBatteryLevel(it)
        }
    }

    private fun normalizeBatteryLevel(batteryLevel: Int): Float {
        val normalizedLevel = batteryLevel / FULL_BATTERY_PCT
        return roundToOneDecimalPlace(normalizedLevel)
    }

    private fun roundToOneDecimalPlace(input: Float): Float {
        return (input * DECIMAL_SCALING).roundToInt() / DECIMAL_SCALING
    }

    private companion object {
        const val FULL_BATTERY_PCT = 100f
        const val DECIMAL_SCALING = 10f
        const val BATTERY_POLL_INTERVAL_MS = 60_000 // 60 seconds
    }
}
