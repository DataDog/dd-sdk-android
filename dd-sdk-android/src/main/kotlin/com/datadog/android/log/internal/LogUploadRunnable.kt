/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.utils.sdkLogger

internal class LogUploadRunnable(
    private val handler: Handler,
    private val logReader: LogReader,
    private val logUploader: LogUploader
) : Runnable {

    private val attemptsCount = mutableMapOf<String, Int>()

    //  region Runnable

    override fun run() {
        val batch = logReader.readNextBatch()

        if (batch != null) {
            val batchId = batch.id
            sdkLogger.i("$TAG: Sending batch $batchId")
            val status = logUploader.uploadLogs(batch.logs)
            if (shouldDropBatch(batchId, status)) {
                logReader.dropBatch(batchId)
            }
        } else {
            sdkLogger.i("$TAG: There was no batch to send")
        }

        handler.postDelayed(this, DELAY_MS)
    }

    // endregion

    // region Internal

    private fun shouldDropBatch(batchId: String, status: LogUploadStatus): Boolean {
        val maxAttempts = maxAttemptsMap[status] ?: 1
        val attemptCount = (attemptsCount[batchId] ?: 0) + 1

        val shouldDropBatch = attemptCount >= maxAttempts
        if (shouldDropBatch) {
            attemptsCount.remove(batchId)
        } else {
            attemptsCount[batchId] = attemptCount
        }
        return shouldDropBatch
    }

    // endregion

    companion object {
        private val maxAttemptsMap = mapOf(
                LogUploadStatus.NETWORK_ERROR to 3,
                LogUploadStatus.HTTP_SERVER_ERROR to 3
        )

        const val DELAY_MS = 5000L
        const val TAG = "LogUploadRunnable"
    }
}
