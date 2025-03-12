/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.internal.telemetry.UploadQualityBlockers
import com.datadog.android.internal.telemetry.UploadQualityCategories
import com.datadog.android.internal.telemetry.UploadQualityEvent
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class DataUploadRunnable(
    private val featureName: String,
    private val threadPoolExecutor: ScheduledThreadPoolExecutor,
    private val storage: Storage,
    private val dataUploader: DataUploader,
    private val contextProvider: ContextProvider,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider,
    internal val uploadSchedulerStrategy: UploadSchedulerStrategy,
    internal val maxBatchesPerJob: Int,
    private val internalLogger: InternalLogger,
    private val uploadQualityListener: UploadQualityListener
) : UploadRunnable {

    //  region Runnable

    @WorkerThread
    override fun run() {
        var uploadAttempts = 0
        var lastBatchUploadStatus: UploadStatus? = null

        if (isNetworkAvailable() && isSystemReady()) {
            val context = contextProvider.context
            var batchConsumerAvailableAttempts = maxBatchesPerJob
            do {
                batchConsumerAvailableAttempts--
                lastBatchUploadStatus = handleNextBatch(context)
                if (lastBatchUploadStatus != null) {
                    uploadAttempts++
                }
            } while (
                batchConsumerAvailableAttempts > 0 && lastBatchUploadStatus is UploadStatus.Success
            )
        }

        logUploadQualityEvents()

        val delayMs = uploadSchedulerStrategy.getMsDelayUntilNextUpload(
            featureName,
            uploadAttempts,
            lastBatchUploadStatus?.code,
            lastBatchUploadStatus?.throwable
        )
        scheduleNextUpload(delayMs)
    }

    // endregion

    // region Internal

    @WorkerThread
    @Suppress("UnsafeThirdPartyFunctionCall") // called inside a dedicated executor
    private fun handleNextBatch(context: DatadogContext): UploadStatus? {
        var uploadStatus: UploadStatus? = null
        val nextBatchData = storage.readNextBatch()
        if (nextBatchData != null) {
            uploadStatus = consumeBatch(
                context,
                nextBatchData.id,
                nextBatchData.data,
                nextBatchData.metadata
            )
        }
        return uploadStatus
    }

    private fun logUploadQualityEvents() {
        uploadQualityListener.onUploadQualityEvent(
            UploadQualityEvent(
                track = featureName,
                category = UploadQualityCategories.COUNT,
                specificType = null
            )
        )

        if (!isNetworkAvailable()) {
            uploadQualityListener.onUploadQualityEvent(
                UploadQualityEvent(
                    track = featureName,
                    category = UploadQualityCategories.BLOCKER,
                    specificType = UploadQualityBlockers.OFFLINE.key
                )
            )
        }

        if (isLowPower()) {
            uploadQualityListener.onUploadQualityEvent(
                UploadQualityEvent(
                    track = featureName,
                    category = UploadQualityCategories.BLOCKER,
                    specificType = UploadQualityBlockers.LOW_BATTERY.key
                )
            )
        }

        if (isPowerSaveMode()) {
            uploadQualityListener.onUploadQualityEvent(
                UploadQualityEvent(
                    track = featureName,
                    category = UploadQualityCategories.BLOCKER,
                    specificType = UploadQualityBlockers.LOW_POWER_MODE.key
                )
            )
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val networkInfo = networkInfoProvider.getLatestNetworkInfo()
        return networkInfo.connectivity != NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
    }

    private fun isPowerSaveMode(): Boolean {
        val systemInfo = systemInfoProvider.getLatestSystemInfo()
        return systemInfo.powerSaveMode
    }

    private fun isLowPower(): Boolean {
        val systemInfo = systemInfoProvider.getLatestSystemInfo()
        return systemInfo.batteryLevel <= LOW_BATTERY_THRESHOLD
    }

    private fun isSystemReady(): Boolean {
        val systemInfo = systemInfoProvider.getLatestSystemInfo()
        val hasEnoughPower = systemInfo.batteryFullOrCharging ||
            systemInfo.onExternalPowerSource ||
            systemInfo.batteryLevel > LOW_BATTERY_THRESHOLD
        return hasEnoughPower && !systemInfo.powerSaveMode
    }

    private fun scheduleNextUpload(delayMs: Long) {
        threadPoolExecutor.remove(this)
        threadPoolExecutor.scheduleSafe(
            "$featureName: data upload",
            delayMs,
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
        val status = dataUploader.upload(context, batch, batchMeta, batchId)
        if (status.code != HTTP_SUCCESS_CODE) {
            uploadQualityListener.onUploadQualityEvent(
                UploadQualityEvent(
                    track = featureName,
                    category = UploadQualityCategories.FAILURE,
                    specificType = status.code.toString()
                )
            )
        }
        val removalReason = if (status is UploadStatus.RequestCreationError) {
            RemovalReason.Invalid
        } else {
            RemovalReason.IntakeCode(status.code)
        }
        storage.confirmBatchRead(batchId, removalReason, deleteBatch = !status.shouldRetry)
        return status
    }

    // endregion

    companion object {
        internal const val LOW_BATTERY_THRESHOLD = 10
        private const val HTTP_SUCCESS_CODE = 202
    }
}
