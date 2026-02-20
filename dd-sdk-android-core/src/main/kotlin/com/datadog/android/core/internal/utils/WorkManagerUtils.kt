/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.UploadWorker
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import java.util.concurrent.TimeUnit

internal const val UPLOAD_WORKER_NAME = "DatadogUploadWorker"
internal const val TAG_DATADOG_UPLOAD = "DatadogBackgroundUpload"

internal const val DELAY_MS: Long = 5000

internal fun cancelUploadWorker(
    context: Context,
    instanceName: String,
    internalLogger: InternalLogger
) {
    try {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("$TAG_DATADOG_UPLOAD/$instanceName")
    } catch (e: IllegalStateException) {
        DdSdkAndroidCoreLogger(internalLogger).logCancelError(e)
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun triggerUploadWorker(
    context: Context,
    instanceName: String,
    internalLogger: InternalLogger
) {
    try {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_ROAMING)
            .build()
        val uploadWorkRequest = OneTimeWorkRequest.Builder(UploadWorker::class.java)
            .setConstraints(constraints)
            .addTag("$TAG_DATADOG_UPLOAD/$instanceName")
            .setInitialDelay(DELAY_MS, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(UploadWorker.DATADOG_INSTANCE_NAME, instanceName).build())
            .build()
        workManager.enqueueUniqueWork(
            UPLOAD_WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            uploadWorkRequest
        )
        DdSdkAndroidCoreLogger(internalLogger).logUploadWorkerWasScheduled()
    } catch (e: Exception) {
        DdSdkAndroidCoreLogger(internalLogger).logSetupError(e)
    }
}
