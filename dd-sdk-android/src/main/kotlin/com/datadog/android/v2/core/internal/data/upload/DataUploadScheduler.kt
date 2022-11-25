/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.data.upload

import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class DataUploadScheduler(
    storage: Storage,
    dataUploader: DataUploader,
    contextProvider: ContextProvider,
    networkInfoProvider: NetworkInfoProvider,
    systemInfoProvider: SystemInfoProvider,
    uploadFrequency: UploadFrequency,
    private val scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor
) : UploadScheduler {

    private val runnable = DataUploadRunnable(
        scheduledThreadPoolExecutor,
        storage,
        dataUploader,
        contextProvider,
        networkInfoProvider,
        systemInfoProvider,
        uploadFrequency
    )

    override fun startScheduling() {
        scheduledThreadPoolExecutor.scheduleSafe(
            "Data upload",
            runnable.currentDelayIntervalMs,
            TimeUnit.MILLISECONDS,
            runnable
        )
    }

    override fun stopScheduling() {
        scheduledThreadPoolExecutor.remove(runnable)
    }
}
