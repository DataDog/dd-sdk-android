/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import androidx.annotation.WorkerThread
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.NetworkInfo
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal class DataUploadRunnable(
    private val threadPoolExecutor: ScheduledThreadPoolExecutor,
    private val reader: DataReader,
    private val dataUploader: DataUploader,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider,
    uploadFrequency: UploadFrequency,
    private val internalLogger: InternalLogger
) : UploadRunnable {

    internal var currentDelayIntervalMs = DEFAULT_DELAY_FACTOR * uploadFrequency.baseStepMs
    internal var minDelayMs = MIN_DELAY_FACTOR * uploadFrequency.baseStepMs
    internal var maxDelayMs = MAX_DELAY_FACTOR * uploadFrequency.baseStepMs

    //  region Runnable

    @WorkerThread
    override fun run() {
        val batch = if (isNetworkAvailable() && isSystemReady()) {
            reader.lockAndReadNext()
        } else {
            null
        }

        if (batch != null) {
            consumeBatch(batch)
        } else {
            increaseInterval()
        }

        scheduleNextUpload()
    }

    // endregion

    // region Internal

    private fun isNetworkAvailable(): Boolean {
        val networkInfo = networkInfoProvider.getLatestNetworkInfo()
        return networkInfo.connectivity != NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
    }

    private fun isSystemReady(): Boolean {
        val systemInfo = systemInfoProvider.getLatestSystemInfo()
        val hasEnoughPower = systemInfo.batteryFullOrCharging ||
            systemInfo.onExternalPowerSource ||
            systemInfo.batteryLevel > LOW_BATTERY_THRESHOLD
        return hasEnoughPower && !systemInfo.powerSaveMode
    }

    private fun scheduleNextUpload() {
        threadPoolExecutor.remove(this)
        threadPoolExecutor.scheduleSafe(
            "Data upload",
            currentDelayIntervalMs,
            TimeUnit.MILLISECONDS,
            internalLogger,
            this
        )
    }

    @WorkerThread
    private fun consumeBatch(batch: Batch) {
        val status = dataUploader.upload(batch.data)

        if (status.shouldRetry) {
            reader.release(batch)
            increaseInterval()
        } else {
            reader.drop(batch)
            decreaseInterval()
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private fun decreaseInterval() {
        currentDelayIntervalMs = max(
            minDelayMs,
            @Suppress("UnsafeThirdPartyFunctionCall") // not a NaN
            (currentDelayIntervalMs * DECREASE_PERCENT).roundToLong()
        )
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private fun increaseInterval() {
        currentDelayIntervalMs = min(
            maxDelayMs,
            @Suppress("UnsafeThirdPartyFunctionCall") // not a NaN
            (currentDelayIntervalMs * INCREASE_PERCENT).roundToLong()
        )
    }

    // endregion

    companion object {
        internal const val LOW_BATTERY_THRESHOLD = 10

        internal const val MIN_DELAY_FACTOR = 1
        internal const val DEFAULT_DELAY_FACTOR = 5
        internal const val MAX_DELAY_FACTOR = 10

        const val DECREASE_PERCENT = 0.90
        const val INCREASE_PERCENT = 1.10
    }
}
