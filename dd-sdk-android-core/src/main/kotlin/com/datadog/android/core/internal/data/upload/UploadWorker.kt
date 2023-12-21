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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.NoOpInternalSdkCore
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.utils.unboundInternalLogger
import java.util.LinkedList
import java.util.Queue

internal class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    // region Worker

    @WorkerThread
    override fun doWork(): Result {
        // the idea behind upload is the following:
        // 1. we shuffle features list to randomize initial upload task sequence. It is done to
        // avoid the possible bottleneck when some feature has big batches which are uploaded
        // slowly, so that next time other features don't wait and have a chance to go before.
        // 2. we introduce FIFO queue also to avoid the bottleneck: if some feature batch cannot
        // be uploaded we put retry task to the end of queue, so that batches of other features
        // have a chance to go.
        val instanceName = inputData.getString(DATADOG_INSTANCE_NAME)
        val sdkCore = Datadog.getInstance(instanceName) as? InternalSdkCore
        if (sdkCore == null || sdkCore is NoOpInternalSdkCore) {
            unboundInternalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { MESSAGE_NOT_INITIALIZED }
            )
            return Result.success()
        }

        val features = sdkCore.getAllFeatures().mapNotNull { it as? SdkFeature }.shuffled()

        val tasksQueue = LinkedList<UploadNextBatchTask>()

        features.forEach {
            @Suppress("UnsafeThirdPartyFunctionCall") // safe to add
            tasksQueue.offer(UploadNextBatchTask(tasksQueue, sdkCore, it))
        }

        while (!tasksQueue.isEmpty()) {
            tasksQueue.poll()?.run()
        }

        return Result.success()
    }

    // endregion

    // region Internal

    class UploadNextBatchTask(
        private val taskQueue: Queue<UploadNextBatchTask>,
        private val sdkCore: InternalSdkCore,
        private val feature: SdkFeature
    ) : Runnable {

        @WorkerThread
        override fun run() {
            // context is unique for each batch query instead of using the same one for all the
            // batches which will be uploaded, because it can change by the time the upload
            // of the next batch is requested.
            val context = sdkCore.getDatadogContext() ?: return

            val storage = feature.storage
            val uploader = feature.uploader
            val nextBatchData = storage.readNextBatch()
            if (nextBatchData != null) {
                val uploadStatus = consumeBatch(
                    context,
                    nextBatchData.data,
                    nextBatchData.metadata,
                    uploader
                )
                storage.confirmBatchRead(
                    nextBatchData.id,
                    RemovalReason.IntakeCode(uploadStatus.code),
                    deleteBatch = !uploadStatus.shouldRetry
                )
                @Suppress("UnsafeThirdPartyFunctionCall") // safe to add
                taskQueue.offer(UploadNextBatchTask(taskQueue, sdkCore, feature))
            }
        }

        private fun consumeBatch(
            context: DatadogContext,
            batch: List<RawBatchEvent>,
            batchMeta: ByteArray?,
            uploader: DataUploader
        ): UploadStatus {
            return uploader.upload(context, batch, batchMeta)
        }
    }

    // endregion

    companion object {

        const val MESSAGE_NOT_INITIALIZED = "Datadog has not been initialized."

        const val DATADOG_INSTANCE_NAME = "_dd.sdk.instanceName"
    }
}
