/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdLogs
import com.datadog.android.log.Logger

internal class BridgeLogs(
    logger: Logger? = null
) : DdLogs {

    private val bridgeLogger: Logger by lazy {
        logger ?: Logger.Builder()
            .setDatadogLogsEnabled(true)
            .setLogcatLogsEnabled(true)
            .setLoggerName("DdLogs")
            .build()
    }

    override fun debug(message: String, context: Map<String, Any?>) {
        bridgeLogger.d(
            message = message,
            attributes = context
        )
    }

    override fun info(message: String, context: Map<String, Any?>) {
        bridgeLogger.i(
            message = message,
            attributes = context
        )
    }

    override fun warn(message: String, context: Map<String, Any?>) {
        bridgeLogger.w(
            message = message,
            attributes = context
        )
    }

    override fun error(message: String, context: Map<String, Any?>) {
        bridgeLogger.e(
            message = message,
            attributes = context
        )
    }
}
