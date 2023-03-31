/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.v2.api.InternalLogger
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class DataUploadScheduler(
    reader: DataReader,
    dataUploader: DataUploader,
    networkInfoProvider: NetworkInfoProvider,
    systemInfoProvider: SystemInfoProvider,
    uploadFrequency: UploadFrequency,
    private val scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor,
    private val internalLogger: InternalLogger
) : UploadScheduler {

    private val runnable = DataUploadRunnable(
        scheduledThreadPoolExecutor,
        reader,
        dataUploader,
        networkInfoProvider,
        systemInfoProvider,
        uploadFrequency,
        internalLogger
    )

    override fun startScheduling() {
        scheduledThreadPoolExecutor.scheduleSafe(
            "Data upload",
            runnable.currentDelayIntervalMs,
            TimeUnit.MILLISECONDS,
            internalLogger,
            runnable
        )
    }

    override fun stopScheduling() {
        scheduledThreadPoolExecutor.remove(runnable)
    }
}
