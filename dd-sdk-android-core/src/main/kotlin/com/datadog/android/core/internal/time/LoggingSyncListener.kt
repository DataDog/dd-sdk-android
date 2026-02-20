/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.lyft.kronos.SyncListener

internal class LoggingSyncListener(private val internalLogger: InternalLogger) : SyncListener {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)
    override fun onStartSync(host: String) {
        // no-op
    }

    override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) {
        // no-op
    }

    override fun onError(host: String, throwable: Throwable) {
        logger.logKronosOnError(host = host, throwable = throwable)
    }
}
