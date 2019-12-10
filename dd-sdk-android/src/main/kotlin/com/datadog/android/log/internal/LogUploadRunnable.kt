/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.utils.sdkLogger
import java.util.concurrent.atomic.AtomicBoolean

internal class LogUploadRunnable(
    private val handler: Handler,
    private val logReader: LogReader,
    private val logUploader: LogUploader,
    private val networkInfoProvider: NetworkInfoProvider
) : UploadRunnable {

    private var currentDelayInterval = DEFAULT_DELAY
    private val isDelayed: AtomicBoolean = AtomicBoolean(false)

    //  region Runnable

    override fun run() {
        val networkInfo = networkInfoProvider.getLatestNetworkInfo()
        isDelayed.set(false)
        val batch =
            if (networkInfo.connectivity != NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED) {
                logReader.readNextBatch()
            } else null
        if (batch != null) {
            consumeBatch(batch)
        } else {
            delayTheRunnable()
        }
    }

    // endregion

    // region Internal

    private fun delayTheRunnable() {
        sdkLogger.i("$TAG: There was no batch to be sent")
        currentDelayInterval = DEFAULT_DELAY
        handler.removeCallbacks(this)
        isDelayed.set(true)
        handler.postDelayed(this, MAX_DELAY)
    }

    private fun consumeBatch(batch: Batch) {
        val batchId = batch.id
        sdkLogger.i("$TAG: Sending batch $batchId")
        val status = logUploader.uploadLogs(batch.logs)
        if (status in dropableBatchStatus) {
            logReader.dropBatch(batchId)
        }
        currentDelayInterval = decreaseInterval()
        handler.postDelayed(this, currentDelayInterval)
    }

    private fun decreaseInterval(): Long {
        return Math.max(MIN_DELAY_MS, currentDelayInterval * DELAY_PERCENT / 100)
    }

    // endregion

    companion object {

        private val dropableBatchStatus = setOf(
            LogUploadStatus.SUCCESS,
            LogUploadStatus.HTTP_REDIRECTION,
            LogUploadStatus.HTTP_CLIENT_ERROR,
            LogUploadStatus.UNKNOWN_ERROR
        )

        const val DEFAULT_DELAY = 5000L // 5 seconds
        const val MIN_DELAY_MS = 1000L // 1 second
        const val MAX_DELAY = DEFAULT_DELAY * 4 // 20 seconds
        const val DELAY_PERCENT = 90 // as 90 percent of
        val TAG = "LogUploadRunnable"
    }
}
