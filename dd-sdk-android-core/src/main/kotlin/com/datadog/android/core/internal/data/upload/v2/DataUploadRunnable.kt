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
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.UploadRunnable
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
    internal val uploadFrequency: UploadFrequency,
    private val batchUploadWaitTimeoutMs: Long = CoreFeature.NETWORK_TIMEOUT_MS,
    private val internalLogger: InternalLogger
) : UploadRunnable {

    internal var currentDelayIntervalMs = DEFAULT_DELAY_FACTOR * uploadFrequency.baseStepMs
    internal var minDelayMs = MIN_DELAY_FACTOR * uploadFrequency.baseStepMs
    internal var maxDelayMs = MAX_DELAY_FACTOR * uploadFrequency.baseStepMs

    //  region Runnable

    @WorkerThread
    @Suppress("UnsafeThirdPartyFunctionCall") // called inside a dedicated executor
    override fun run() {
        if (isNetworkAvailable() && isSystemReady()) {
            val context = contextProvider.context
            // TODO RUMM-0000 it should be already on the worker thread and if readNextBatch is async,
            //  we should wait until it completes before scheduling further
            val lock = CountDownLatch(1)
            storage.readNextBatch(
                noBatchCallback = {
                    increaseInterval()
                    lock.countDown()
                }
            ) { batchId, reader ->
                try {
                    val batch = reader.read()
                    val batchMeta = reader.currentMetadata()

                    consumeBatch(
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
        }

        scheduleNextUpload()
    }

    // endregion

    // region Internal

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
        batch: List<ByteArray>,
        batchMeta: ByteArray?
    ) {
        val status = dataUploader.upload(context, batch, batchMeta)

        storage.confirmBatchRead(batchId) {
            if (status.shouldRetry) {
                it.markAsRead(false)
                increaseInterval()
            } else {
                it.markAsRead(true)
                decreaseInterval()
            }
        }
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

        internal const val MIN_DELAY_FACTOR = 1
        internal const val DEFAULT_DELAY_FACTOR = 5
        internal const val MAX_DELAY_FACTOR = 10

        const val DECREASE_PERCENT = 0.90
        const val INCREASE_PERCENT = 1.10
    }
}
