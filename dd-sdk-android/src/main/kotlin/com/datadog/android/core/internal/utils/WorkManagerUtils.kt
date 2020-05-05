/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.datadog.android.core.internal.data.upload.UploadWorker
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit

internal const val CANCEL_ERROR_MESSAGE = "Error cancelling the UploadWorker"
internal const val SETUP_ERROR_MESSAGE = "Error while trying to setup the upload worker."
internal const val UPLOAD_WORKER_NAME = "DatadogUploadWorker"
internal const val TAG_DATADOG_UPLOAD = "DatadogBackgroundUpload"

internal const val DELAY_MS: Long = 5000

internal fun cancelUploadWorker(context: Context) {
    sdkLogger.i("Cancelling UploadWorker")
    try {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(TAG_DATADOG_UPLOAD)
    } catch (e: IllegalStateException) {
        sdkLogger.e(CANCEL_ERROR_MESSAGE, e)
    }
}

internal fun triggerUploadWorker(context: Context) {
    try {
        sdkLogger.i("Triggering UploadWorker")
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadWorkRequest = OneTimeWorkRequest.Builder(UploadWorker::class.java)
            .setConstraints(constraints)
            .addTag(TAG_DATADOG_UPLOAD)
            .setInitialDelay(DELAY_MS, TimeUnit.MILLISECONDS)
            .build()
        workManager
            .enqueueUniqueWork(
                UPLOAD_WORKER_NAME,
                ExistingWorkPolicy.REPLACE,
                uploadWorkRequest
            )
    } catch (e: IllegalStateException) {
        sdkLogger.e(SETUP_ERROR_MESSAGE, e)
    }
}
