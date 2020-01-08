/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.core.internal.data

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.utils.sdkLogger

internal class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    // region Worker

    override fun doWork(): Result {
        val reader = Datadog.getLogStrategy().getReader()
        val uploader = Datadog.getLogUploader()

        val failedBatches = mutableListOf<Batch>()
        var batch: Batch?
        do {
            batch = reader.readNextBatch()
            if (batch != null) {
                if (consumeBatch(batch, uploader)) {
                    reader.dropBatch(batch.id)
                } else {
                    failedBatches.add(batch)
                }
            }
        } while (batch != null)

        failedBatches.forEach {
            reader.releaseBatch(it.id)
        }

        return Result.success()
    }

    // endregion

    // region Internal

    private fun consumeBatch(
        batch: Batch,
        uploader: LogUploader
    ): Boolean {
        val batchId = batch.id
        sdkLogger.i("$TAG: Sending batch $batchId")
        val status = uploader.upload(batch.data)
        return status == LogUploadStatus.SUCCESS
    }

    // endregion

    companion object {
        private const val TAG = "UploadWorker"
    }
}
