/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdLogs
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler

internal class BridgeLogs(
    private val logHandler: LogHandler? = null
) : DdLogs {

    private val logger: Logger by lazy {
        if (logHandler == null) {
            Logger.Builder()
                .setDatadogLogsEnabled(true)
                .setLogcatLogsEnabled(true)
                .setLoggerName("DdLogs")
                .build()
        } else {
            Logger(logHandler)
        }
    }

    override fun debug(message: String, context: Map<String, Any?>) {
        logger.d(
            message = message,
            attributes = context
        )
    }

    override fun info(message: String, context: Map<String, Any?>) {
        logger.i(
            message = message,
            attributes = context
        )
    }

    override fun warn(message: String, context: Map<String, Any?>) {
        logger.w(
            message = message,
            attributes = context
        )
    }

    override fun error(message: String, context: Map<String, Any?>) {
        logger.e(
            message = message,
            attributes = context
        )
    }
}
