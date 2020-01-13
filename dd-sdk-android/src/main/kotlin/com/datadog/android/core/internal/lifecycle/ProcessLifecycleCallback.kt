package com.datadog.android.core.internal.lifecycle

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.datadog.android.core.internal.data.UploadWorker
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.utils.sdkLogger
import java.lang.IllegalStateException
import java.lang.ref.WeakReference

internal class ProcessLifecycleCallback(
    val networkInfoProvider: NetworkInfoProvider,
    appContext: Context
) :
    ProcessLifecycleMonitor.Callback {

    private val contextWeakRef = WeakReference<Context>(appContext)

    override fun onStarted() {
        // NO - OP
    }

    override fun onResumed() {
        // NO - OP
    }

    override fun onStopped() {
        val isOffline = (networkInfoProvider.getLatestNetworkInfo().connectivity
                == NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
        if (isOffline) {
            contextWeakRef.get()?.let {
                triggerWorkManagerTask(it)
            }
        }
    }

    override fun onPaused() {
        // NO - OP
    }

    private fun triggerWorkManagerTask(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val uploadWorkRequest = OneTimeWorkRequest.Builder(UploadWorker::class.java)
                .setConstraints(constraints)
                .build()
            workManager
                .enqueueUniqueWork(UPLOAD_WORKER_TAG, ExistingWorkPolicy.REPLACE, uploadWorkRequest)
        } catch (e: IllegalStateException) {
            sdkLogger.e(TAG, e)
        }
    }

    companion object {
        const val UPLOAD_WORKER_TAG = "UploadWorker"
        const val TAG = "ProcessLifecycleCallback"
    }
}
