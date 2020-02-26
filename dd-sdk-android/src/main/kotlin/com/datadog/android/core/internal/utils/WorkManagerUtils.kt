package com.datadog.android.core.internal.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.datadog.android.core.internal.data.upload.UploadWorker
import java.lang.IllegalStateException

internal const val TAG = "triggerUploadWorker"
internal const val UPLOAD_WORKER_TAG = "UploadWorker"

internal fun triggerUploadWorker(context: Context) {
    try {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadWorkRequest = OneTimeWorkRequest.Builder(UploadWorker::class.java)
            .setConstraints(constraints)
            .build()
        workManager
            .enqueueUniqueWork(
                UPLOAD_WORKER_TAG,
                ExistingWorkPolicy.REPLACE,
                uploadWorkRequest
            )
    } catch (e: IllegalStateException) {
        sdkLogger.e(TAG, e)
    }
}
