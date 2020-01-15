package com.datadog.android.core.internal.lifecycle

import android.content.Context
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.utils.triggerUploadWorker
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
                triggerUploadWorker(it)
            }
        }
    }

    override fun onPaused() {
        // NO - OP
    }
}
