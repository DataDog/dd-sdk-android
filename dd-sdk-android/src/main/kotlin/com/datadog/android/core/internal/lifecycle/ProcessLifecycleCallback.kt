/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.lifecycle

import android.content.Context
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.utils.cancelUploadWorker
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.core.model.NetworkInfo
import java.lang.ref.WeakReference

internal class ProcessLifecycleCallback(
    val networkInfoProvider: NetworkInfoProvider,
    appContext: Context
) :
    ProcessLifecycleMonitor.Callback {

    private val contextWeakRef = WeakReference<Context>(appContext)

    override fun onStarted() {
        contextWeakRef.get()?.let {
            cancelUploadWorker(it)
        }
    }

    override fun onResumed() {
        // NO - OP
    }

    override fun onStopped() {
        val isOffline = (
            networkInfoProvider.getLatestNetworkInfo().connectivity
                == NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
            )
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
