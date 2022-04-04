/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.util.Log as AndroidLog
import com.datadog.android.telemetry.internal.Telemetry

internal class TelemetryLogHandler(private val telemetry: Telemetry) : LogHandler {
    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        if (level == AndroidLog.ERROR || level == AndroidLog.WARN) {
            telemetry.error(message, throwable)
        } else {
            telemetry.debug(message)
        }
    }
}
