/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.lifecycle

import android.content.Context
import androidx.work.WorkManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.cancelUploadWorker
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor

internal class ProcessLifecycleCallback(
    private val appContext: Context,
    internal val instanceName: String,
    private val internalLogger: InternalLogger
) : ProcessLifecycleMonitor.Callback {

    override fun onStarted() {
        if (WorkManager.isInitialized()) {
            cancelUploadWorker(appContext, instanceName, internalLogger)
        }
    }

    override fun onResumed() {
        // NO - OP
    }

    override fun onStopped() {
        if (WorkManager.isInitialized()) {
            triggerUploadWorker(appContext, instanceName, internalLogger)
        }
    }

    override fun onPaused() {
        // NO - OP
    }
}
