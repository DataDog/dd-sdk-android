/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload.v2

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.UploadRunnable
import com.datadog.android.core.internal.data.upload.UploadStatus
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal class DataUploadRunnable(
    private val threadPoolExecutor: ScheduledThreadPoolExecutor,
    private val storage: Storage,
    private val dataUploader: DataUploader,
    private val contextProvider: ContextProvider,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider,
    uploadConfiguration: DataUploadConfiguration,
    private val batchUploadWaitTimeoutMs: Long = CoreFeature.NETWORK_TIMEOUT_MS,
    private val internalLogger: InternalLogger
) : UploadRunnable {

    internal var currentDelayIntervalMs = uploadConfiguration.defaultDelayMs
    internal val minDelayMs = uploadConfiguration.minDelayMs
    internal val maxDelayMs = uploadConfiguration.maxDelayMs
    internal val maxBatchesPerJob = uploadConfiguration.maxBatchesPerUploadJob

    //  region Runnable

    @WorkerThread
    override fun run() {
        if (isNetworkAvailable() && isSystemReady()) {
            val context = contextProvider.context
            // TODO RUMM-0000 it should be already on the worker thread and if readNextBatch is async,
            //  we should wait until it completes before scheduling further
            var batchConsumerAvailableAttempts = maxBatchesPerJob
            var lastBatchUploadStatus: UploadStatus?
            do {
                batchConsumerAvailableAttempts--
                lastBatchUploadStatus = handleNextBatch(context)
            } while (batchConsumerAvailableAttempts > 0 &&
                lastBatchUploadStatus is UploadStatus.Success
            )
            if (lastBatchUploadStatus != null) {
                handleBatchConsumingJobFrequency(lastBatchUploadStatus)
            } else {
                // there was no batch left or there was a problem reading the next batch
                // in the storage so we increase the interval
                increaseInterval()
            }
        }

        scheduleNextUpload()
    }

    // endregion

    // region Internal

    private fun handleBatchConsumingJobFrequency(lastBatchUploadStatus: UploadStatus) {
        if (lastBatchUploadStatus.shouldRetry) {
            increaseInterval()
        } else {
            decreaseInterval()
        }
    }

    @WorkerThread
    @Suppress("UnsafeThirdPartyFunctionCall") // called inside a dedicated executor
    private fun handleNextBatch(context: DatadogContext): UploadStatus? {
        var uploadStatus: UploadStatus? = null
        val lock = CountDownLatch(1)
        storage.readNextBatch(
            noBatchCallback = {
                lock.countDown()
            }
        ) { batchId, reader ->
            try {
                val batch = reader.read()
                val batchMeta = reader.currentMetadata()

                uploadStatus = consumeBatch(
                    context,
                    batchId,
                    batch,
                    batchMeta
                )
            } finally {
                lock.countDown()
            }
        }
        lock.await(batchUploadWaitTimeoutMs, TimeUnit.MILLISECONDS)
        return uploadStatus
    }

    private fun isNetworkAvailable(): Boolean {
        val networkInfo = networkInfoProvider.getLatestNetworkInfo()
        return networkInfo.connectivity != NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
    }

    private fun isSystemReady(): Boolean {
        val systemInfo = systemInfoProvider.getLatestSystemInfo()
        val hasEnoughPower = systemInfo.batteryFullOrCharging ||
            systemInfo.onExternalPowerSource ||
            systemInfo.batteryLevel > LOW_BATTERY_THRESHOLD
        return hasEnoughPower && !systemInfo.powerSaveMode
    }

    private fun scheduleNextUpload() {
        threadPoolExecutor.remove(this)
        threadPoolExecutor.scheduleSafe(
            "Data upload",
            currentDelayIntervalMs,
            TimeUnit.MILLISECONDS,
            internalLogger,
            this
        )
    }

    @WorkerThread
    private fun consumeBatch(
        context: DatadogContext,
        batchId: BatchId,
        batch: List<RawBatchEvent>,
        batchMeta: ByteArray?
    ): UploadStatus {
        val status = dataUploader.upload(context, batch, batchMeta)
        val removalReason = if (status is UploadStatus.RequestCreationError) {
            RemovalReason.Invalid
        } else {
            RemovalReason.IntakeCode(status.code)
        }
        storage.confirmBatchRead(batchId, removalReason) {
            it.markAsRead(deleteBatch = !status.shouldRetry)
        }
        return status
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private fun decreaseInterval() {
        currentDelayIntervalMs = max(
            minDelayMs,
            @Suppress("UnsafeThirdPartyFunctionCall") // not a NaN
            (currentDelayIntervalMs * DECREASE_PERCENT).roundToLong()
        )
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private fun increaseInterval() {
        currentDelayIntervalMs = min(
            maxDelayMs,
            @Suppress("UnsafeThirdPartyFunctionCall") // not a NaN
            (currentDelayIntervalMs * INCREASE_PERCENT).roundToLong()
        )
    }

    // endregion

    companion object {
        internal const val LOW_BATTERY_THRESHOLD = 10
        const val DECREASE_PERCENT = 0.90
        const val INCREASE_PERCENT = 1.10
    }
}
