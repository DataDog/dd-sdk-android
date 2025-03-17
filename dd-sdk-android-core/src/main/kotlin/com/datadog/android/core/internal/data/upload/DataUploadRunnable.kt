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
import com.datadog.android.internal.telemetry.UploadQualityBlocker
import com.datadog.android.internal.telemetry.UploadQualityCategory
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

    private var delayMs: Long = 0

    //  region Runnable

    @WorkerThread
    override fun run() {
        var uploadAttempts = 0
        var lastBatchUploadStatus: UploadStatus? = null

        logUploadCycleForTelemetry()

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

        delayMs = uploadSchedulerStrategy.getMsDelayUntilNextUpload(
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

    private fun logUploadCycleForTelemetry() {
        uploadQualityListener.onUploadQualityEvent(
            UploadQualityEvent(
                track = featureName,
                category = UploadQualityCategory.COUNT,
                uploadDelay = delayMs.toInt()
            )
        )

        val blockersList = mutableListOf<String>()

        if (!isNetworkAvailable()) {
            blockersList.add(UploadQualityBlocker.OFFLINE.key)
        }

        if (isLowPower()) {
            blockersList.add(UploadQualityBlocker.LOW_BATTERY.key)
        }

        if (isPowerSaveMode()) {
            blockersList.add(UploadQualityBlocker.LOW_POWER_MODE.key)
        }

        if (blockersList.isNotEmpty()) {
            uploadQualityListener.onUploadQualityEvent(
                UploadQualityEvent(
                    track = featureName,
                    category = UploadQualityCategory.BLOCKER,
                    uploadDelay = delayMs.toInt(),
                    blockers = blockersList
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
        if (status.code != HTTP_ACCEPTED) {
            uploadQualityListener.onUploadQualityEvent(
                UploadQualityEvent(
                    track = featureName,
                    category = UploadQualityCategory.FAILURE,
                    uploadDelay = delayMs.toInt(),
                    failure = status.code.toString()
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
        private const val HTTP_ACCEPTED = 202
    }
}
