/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.file.Batch
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.sdkLogger
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

internal class DataUploadRunnable(
    private val threadPoolExecutor: ScheduledThreadPoolExecutor,
    private val reader: Reader,
    private val dataUploader: DataUploader,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider
) : UploadRunnable {

    private var currentDelayInterval = DEFAULT_DELAY_MS

    //  region Runnable

    override fun run() {
        val batch = if (isNetworkAvailable() && isSystemReady()) {
            reader.readNextBatch()
        } else null

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
        val batteryFullOrCharging = systemInfo.batteryStatus in batteryFullOrChargingStatus
        val batteryLevel = systemInfo.batteryLevel
        val powerSaveMode = systemInfo.powerSaveMode
        return (batteryFullOrCharging || batteryLevel > LOW_BATTERY_THRESHOLD) && !powerSaveMode
    }

    private fun scheduleNextUpload() {
        threadPoolExecutor.remove(this)
        threadPoolExecutor.schedule(this, currentDelayInterval, TimeUnit.MILLISECONDS)
    }

    private fun consumeBatch(batch: Batch) {
        val batchId = batch.id
        sdkLogger.i("Sending batch $batchId")
        val status = dataUploader.upload(batch.data)
        status.logStatus(dataUploader.javaClass.simpleName, batch.data.size)
        if (status in droppableBatchStatus) {
            reader.dropBatch(batchId)
            decreaseInterval()
        } else {
            reader.releaseBatch(batchId)
            increaseInterval()
        }
    }

    private fun decreaseInterval() {
        currentDelayInterval = max(MIN_DELAY_MS, currentDelayInterval * DECREASE_PERCENT / 100)
    }

    private fun increaseInterval() {
        currentDelayInterval = min(MAX_DELAY_MS, currentDelayInterval * INCREASE_PERCENT / 100)
    }

    // endregion

    companion object {

        private val droppableBatchStatus = setOf(
            UploadStatus.SUCCESS,
            UploadStatus.HTTP_REDIRECTION,
            UploadStatus.HTTP_CLIENT_ERROR,
            UploadStatus.UNKNOWN_ERROR
        )

        private val batteryFullOrChargingStatus = setOf(
            SystemInfo.BatteryStatus.CHARGING,
            SystemInfo.BatteryStatus.FULL
        )

        private const val LOW_BATTERY_THRESHOLD = 10

        const val DEFAULT_DELAY_MS = 5000L // 5 seconds
        const val MIN_DELAY_MS = 1000L // 1 second
        const val MAX_DELAY_MS = DEFAULT_DELAY_MS * 4 // 20 seconds
        const val DECREASE_PERCENT = 90
        const val INCREASE_PERCENT = 110
    }
}
