/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.core.internal.utils.sdkLogger
import com.lyft.kronos.SyncListener

internal class LoggingSyncListener : SyncListener {
    override fun onStartSync(host: String) {
        sdkLogger.d("onStartSync @host:$host")
    }

    override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) {
        sdkLogger.d("onSuccess @ticksDelta:$ticksDelta @responseTimeMs:$responseTimeMs")
    }

    override fun onError(host: String, throwable: Throwable) {
        sdkLogger.e("onError @host:host", throwable)
    }
}
