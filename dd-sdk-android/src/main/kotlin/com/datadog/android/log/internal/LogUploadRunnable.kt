/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader

internal class LogUploadRunnable(private val handler: LogHandler) : Runnable {

    private val logReader: LogReader = Datadog.getLogStrategy().getLogReader()
    private val logUploader: LogUploader = Datadog.getLogUploader()

    //  region Runnable

    override fun run() {
        val batch = logReader.readNextBatch()

        if (batch != null) {
                Log.i("T", "Sending batch ${batch.first}")
            val status = logUploader.uploadLogs(batch.second)
            if (status == LogUploadStatus.SUCCESS) {
                logReader.onBatchSent(batch.first)
            }
        } else {
            Log.d("T", "No batch to send")
        }

        handler.postDelayed(this, DELAY_MS)
    }

    // endregion

    companion object {
        const val DELAY_MS = 5000L
    }
}
