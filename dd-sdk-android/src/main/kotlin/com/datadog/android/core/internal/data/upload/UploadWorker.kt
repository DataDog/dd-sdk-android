/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.tracing.internal.TracesFeature

internal class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    // region Worker

    override fun doWork(): Result {
        if (!Datadog.isInitialized()) {
            devLogger.e(Datadog.MESSAGE_NOT_INITIALIZED)
            return Result.success()
        }

        // Upload Crash reports
        uploadAllBatches(
            CrashReportsFeature.persistenceStrategy.getReader(),
            CrashReportsFeature.uploader
        )

        // Upload Logs
        uploadAllBatches(
            LogsFeature.persistenceStrategy.getReader(),
            LogsFeature.uploader
        )

        // Upload Traces
        uploadAllBatches(
            TracesFeature.persistenceStrategy.getReader(),
            TracesFeature.uploader
        )

        return Result.success()
    }

    private fun uploadAllBatches(
        reader: DataReader,
        uploader: DataUploader
    ) {
        val failedBatches = mutableListOf<Batch>()
        var batch: Batch?
        do {
            batch = reader.lockAndReadNext()
            if (batch != null) {
                if (consumeBatch(batch, uploader)) {
                    reader.drop(batch)
                } else {
                    failedBatches.add(batch)
                }
            }
        } while (batch != null)

        failedBatches.forEach {
            reader.release(it)
        }
    }

    // endregion

    // region Internal

    private fun consumeBatch(
        batch: Batch,
        uploader: DataUploader
    ): Boolean {
        val batchId = batch.id
        sdkLogger.i("$TAG: Sending batch $batchId")
        val status = uploader.upload(batch.data)
        status.logStatus(uploader.javaClass.simpleName, batch.data.size)
        return status == UploadStatus.SUCCESS
    }

    // endregion

    companion object {
        private const val TAG = "UploadWorker"
    }
}
