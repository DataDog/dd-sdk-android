/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.log.internal.utils.TELEMETRY_LOG_FLAG

/*
[LogHandler] for the internal logging purpose. It will do a conditional dispatch between logcat and
 telemetry, the latter will be used only if [TELEMETRY_LOG_FLAG] is specified.
 */
internal class InternalLogHandler(
    internal val logcatLogHandler: LogHandler,
    internal val telemetryLogHandler: LogHandler
) : LogHandler {
    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        val reportLevel = level and TELEMETRY_LOG_FLAG.inv()
        logcatLogHandler.handleLog(
            reportLevel,
            message,
            throwable,
            attributes,
            tags,
            timestamp
        )

        val forwardTelemetry = (level and TELEMETRY_LOG_FLAG) != 0

        if (forwardTelemetry) {
            telemetryLogHandler.handleLog(
                reportLevel,
                message,
                throwable,
                attributes,
                tags,
                timestamp
            )
        }
    }

    override fun handleLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStacktrace: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        val reportLevel = level and TELEMETRY_LOG_FLAG.inv()
        logcatLogHandler.handleLog(
            reportLevel,
            message,
            errorKind,
            errorMessage,
            errorStacktrace,
            attributes,
            tags,
            timestamp
        )

        val forwardTelemetry = (level and TELEMETRY_LOG_FLAG) != 0

        if (forwardTelemetry) {
            telemetryLogHandler.handleLog(
                reportLevel,
                message,
                errorKind,
                errorMessage,
                errorStacktrace,
                attributes,
                tags,
                timestamp
            )
        }
    }
}
