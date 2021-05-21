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
        sdkLogger.d(
            "Kronos onStartSync $host",
            attributes = mapOf("kronos.sync.host" to host)
        )
    }

    override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) {
        sdkLogger.d(
            "Kronos onSuccess @ticksDelta:$ticksDelta @responseTimeMs:$responseTimeMs",
            attributes = mapOf(
                "kronos.sync.tick_delta" to ticksDelta,
                "kronos.sync.response_time_ms" to responseTimeMs
            )
        )
    }

    override fun onError(host: String, throwable: Throwable) {
        sdkLogger.e(
            "Kronos onError @host:host",
            throwable,
            attributes = mapOf("kronos.sync.host" to host)
        )
    }
}
