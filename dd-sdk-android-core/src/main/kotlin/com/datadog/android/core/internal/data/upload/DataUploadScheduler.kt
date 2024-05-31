/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.utils.executeSafe
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class DataUploadScheduler(
    private val featureName: String,
    storage: Storage,
    dataUploader: DataUploader,
    contextProvider: ContextProvider,
    networkInfoProvider: NetworkInfoProvider,
    systemInfoProvider: SystemInfoProvider,
    uploadConfiguration: DataUploadConfiguration,
    private val scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor,
    private val internalLogger: InternalLogger
) : UploadScheduler {

    internal val runnable = DataUploadRunnable(
        featureName,
        scheduledThreadPoolExecutor,
        storage,
        dataUploader,
        contextProvider,
        networkInfoProvider,
        systemInfoProvider,
        uploadConfiguration,
        internalLogger = internalLogger
    )

    override fun startScheduling() {
        scheduledThreadPoolExecutor.executeSafe(
            "$featureName: data upload",
            internalLogger,
            runnable
        )
    }

    override fun stopScheduling() {
        scheduledThreadPoolExecutor.remove(runnable)
    }
}
