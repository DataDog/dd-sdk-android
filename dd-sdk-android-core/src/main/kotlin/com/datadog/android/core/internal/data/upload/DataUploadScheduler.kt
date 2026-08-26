/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class DataUploadScheduler(
    private val featureName: String,
    storage: Storage,
    dataUploader: DataUploader,
    contextProvider: ContextProvider,
    networkInfoProvider: NetworkInfoProvider,
    systemInfoProvider: SystemInfoProvider,
    uploadSchedulerStrategy: UploadSchedulerStrategy,
    maxBatchesPerJob: Int,
    private val scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor,
    private val internalLogger: InternalLogger,
    internal val runnable: Callable<Long> = DataUploadTask(
        featureName = featureName,
        storage = storage,
        dataUploader = dataUploader,
        contextProvider = contextProvider,
        networkInfoProvider = networkInfoProvider,
        systemInfoProvider = systemInfoProvider,
        uploadSchedulerStrategy = uploadSchedulerStrategy,
        maxBatchesPerJob = maxBatchesPerJob
    )
) : UploadScheduler {

    private val scheduleLock = Any()
    private var stopped = false

    @Suppress("ObjectLiteralToLambda") // a lambda can't carry the @WorkerThread annotation run() needs
    private val uploadTask = object : Runnable {
        @WorkerThread
        @Suppress("UnsafeThirdPartyFunctionCall") // called inside a dedicated executor
        override fun run() {
            synchronized(scheduleLock) {
                if (stopped) return
            }
            scheduleNext(runnable.call())
        }
    }

    override fun startScheduling() {
        scheduleNext(0)
    }

    override fun stopScheduling() {
        // Fire and forget: the current or queued cycle is left to run, not canceled. Cancelling a
        // future that's already executing only poisons its terminal state to CANCELLED, which
        // LoggingScheduledThreadPoolExecutor then logs as a false ERROR, and there's no safe way
        // to tell "queued" from "running" from here. The stopped flag below makes any cycle that
        // does fire a no-op instead.
        synchronized(scheduleLock) {
            stopped = true
        }
    }

    private fun scheduleNext(delayMs: Long) {
        synchronized(scheduleLock) {
            if (stopped) return
            scheduledThreadPoolExecutor.scheduleSafe(
                "$featureName: data upload",
                delayMs,
                TimeUnit.MILLISECONDS,
                internalLogger,
                uploadTask
            )
        }
    }
}
