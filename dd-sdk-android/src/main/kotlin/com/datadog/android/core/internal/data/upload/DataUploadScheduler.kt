/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class DataUploadScheduler(
    reader: Reader,
    dataUploader: DataUploader,
    networkInfoProvider: NetworkInfoProvider,
    systemInfoProvider: SystemInfoProvider,
    uploadFrequency: UploadFrequency,
    private val scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor
) : UploadScheduler {

    private val runnable =
        DataUploadRunnable(
            scheduledThreadPoolExecutor,
            reader,
            dataUploader,
            networkInfoProvider,
            systemInfoProvider,
            uploadFrequency
        )

    override fun startScheduling() {
        scheduledThreadPoolExecutor.schedule(
            runnable,
            runnable.currentDelayIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    override fun stopScheduling() {
        scheduledThreadPoolExecutor.remove(runnable)
    }
}
