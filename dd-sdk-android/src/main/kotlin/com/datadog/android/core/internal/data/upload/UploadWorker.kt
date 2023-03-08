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
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.internal.net.DataUploader
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION") // TODO RUMM-3103 remove deprecated references
internal class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    // region Worker

    @WorkerThread
    override fun doWork(): Result {
        if (!Datadog.isInitialized()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                Datadog.MESSAGE_NOT_INITIALIZED
            )
            return Result.success()
        }

        val globalSdkCore: DatadogCore? = (Datadog.globalSdkCore as? DatadogCore)

        if (globalSdkCore != null) {
            // the idea behind upload is the following:
            // 1. we shuffle features list to randomize initial upload task sequence. It is done to
            // avoid the possible bottleneck when some feature has big batches which are uploaded
            // slowly, so that next time other features don't wait and have a chance to go before.
            // 2. we introduce FIFO queue also to avoid the bottleneck: if some feature batch cannot
            // be uploaded we put retry task to the end of queue, so that batches of other features
            // have a chance to go.
            val features =
                globalSdkCore.getAllFeatures().mapNotNull { it as? SdkFeature }.shuffled()

            val tasksQueue = LinkedList<UploadNextBatchTask>()

            features.forEach {
                @Suppress("UnsafeThirdPartyFunctionCall") // safe to add
                tasksQueue.offer(UploadNextBatchTask(tasksQueue, globalSdkCore, it))
            }

            while (!tasksQueue.isEmpty()) {
                tasksQueue.poll()?.run()
            }
        }

        return Result.success()
    }

    // endregion

    // region Internal

    class UploadNextBatchTask(
        private val taskQueue: Queue<UploadNextBatchTask>,
        private val datadogCore: DatadogCore,
        private val feature: SdkFeature
    ) : Runnable {

        @WorkerThread
        override fun run() {
            // context is unique for each batch query instead of using the same one for all the
            // batches which will be uploaded, because it can change by the time the upload
            // of the next batch is requested.
            val context = datadogCore.contextProvider?.context ?: return

            val storage = feature.storage
            val uploader = feature.uploader

            // storage APIs may be async, so we need to block current thread to keep Worker alive
            @Suppress("UnsafeThirdPartyFunctionCall") // safe to create, argument is not negative
            val lock = CountDownLatch(1)

            storage.readNextBatch(noBatchCallback = {
                lock.countDown()
            }) { batchId, reader ->
                val batch = reader.read()
                val batchMeta = reader.currentMetadata()

                val success = consumeBatch(context, batch, batchMeta, uploader)
                storage.confirmBatchRead(batchId) { confirmation ->
                    confirmation.markAsRead(deleteBatch = success)
                    @Suppress("UnsafeThirdPartyFunctionCall") // safe to add
                    taskQueue.offer(UploadNextBatchTask(taskQueue, datadogCore, feature))
                    lock.countDown()
                }
            }

            @Suppress("UnsafeThirdPartyFunctionCall") // if interrupt happens, WorkManager
            // will handle it
            lock.await(LOCK_AWAIT_SECONDS, TimeUnit.SECONDS)
        }

        private fun consumeBatch(
            context: DatadogContext,
            batch: List<ByteArray>,
            batchMeta: ByteArray?,
            uploader: DataUploader
        ): Boolean {
            val status = uploader.upload(context, batch, batchMeta)
            return status == UploadStatus.SUCCESS
        }
    }

    // endregion

    companion object {
        const val LOCK_AWAIT_SECONDS = 30L
    }
}
