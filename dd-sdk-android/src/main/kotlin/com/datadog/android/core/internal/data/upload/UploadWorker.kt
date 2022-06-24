/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.DatadogFeature
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage

internal class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    // region Worker

    @WorkerThread
    override fun doWork(): Result {
        if (!Datadog.isInitialized()) {
            devLogger.e(Datadog.MESSAGE_NOT_INITIALIZED)
            return Result.success()
        }

        val globalSDKCore: DatadogCore? = (Datadog.globalSDKCore as? DatadogCore)

        if (globalSDKCore != null) {
            val features =
                globalSDKCore.getAllFeatures().mapNotNull { it as? DatadogFeature }

            features.forEach {
                // TODO RUMM-2296 do interleaving/randomization for the upload sequence, because
                //  if some feature upload is slow, all other feature uploads will wait until
                //  feature completes with all its batches
                uploadNextBatch(
                    globalSDKCore,
                    it.storage,
                    it.uploader
                )
            }
        }

        return Result.success()
    }

    @WorkerThread
    private fun uploadNextBatch(
        datadogCore: DatadogCore,
        storage: Storage,
        uploader: DataUploader
    ) {
        // context is unique for each batch query instead of using the same one for all the batches
        // which will be uploaded, because it can change by the time the upload of the next batch
        // is requested.
        val context = datadogCore.context ?: return

        storage.readNextBatch(context) { batchId, reader ->
            val batch = reader.read()
            val batchMeta = reader.currentMetadata()

            val success = consumeBatch(context, batch, batchMeta, uploader)
            storage.confirmBatchRead(batchId) { confirmation ->
                confirmation.markAsRead(deleteBatch = success)
            }

            // TODO RUMM-2295 stack overflow protection?
            uploadNextBatch(datadogCore, storage, uploader)
        }
    }

    // endregion

    // region Internal

    private fun consumeBatch(
        context: DatadogContext,
        batch: List<ByteArray>,
        batchMeta: ByteArray?,
        uploader: DataUploader
    ): Boolean {
        val status = uploader.upload(context, batch, batchMeta)
        return status == UploadStatus.SUCCESS
    }

    // endregion
}
