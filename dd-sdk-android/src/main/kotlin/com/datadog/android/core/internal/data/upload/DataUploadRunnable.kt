/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.os.Handler
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.sdkLogger
import kotlin.math.max

internal class DataUploadRunnable(
    private val handler: Handler,
    private val reader: Reader,
    private val dataUploader: DataUploader,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider
) : UploadRunnable {

    private var currentDelayInterval =
        DEFAULT_DELAY

    //  region Runnable

    override fun run() {
        val batch = if (isNetworkAvailable() && isSystemReady()) {
            reader.readNextBatch()
        } else null
        if (batch != null) {
            consumeBatch(batch)
        } else {
            delayTheRunnable()
        }
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

    private fun delayTheRunnable() {
        sdkLogger.i("$TAG: There was no batch to be sent")
        currentDelayInterval =
            DEFAULT_DELAY
        handler.removeCallbacks(this)
        handler.postDelayed(this,
            MAX_DELAY
        )
    }

    private fun consumeBatch(batch: Batch) {
        val batchId = batch.id
        sdkLogger.i("$TAG: Sending batch $batchId")
        val status = dataUploader.upload(batch.data)
        status.logStatus(dataUploader.javaClass.simpleName)
        if (status in dropableBatchStatus) {
            reader.dropBatch(batchId)
        } else {
            reader.releaseBatch(batchId)
        }
        currentDelayInterval = decreaseInterval()
        handler.postDelayed(this, currentDelayInterval)
    }

    private fun decreaseInterval(): Long {
        return max(MIN_DELAY_MS, currentDelayInterval * DELAY_PERCENT / 100)
    }

    // endregion

    companion object {

        private val dropableBatchStatus = setOf(
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

        const val DEFAULT_DELAY = 5000L // 5 seconds
        const val MIN_DELAY_MS = 1000L // 1 second
        const val MAX_DELAY = DEFAULT_DELAY * 4 // 20 seconds
        const val DELAY_PERCENT = 90 // as 90 percent of
        private const val TAG = "DataUploadRunnable"
    }
}
