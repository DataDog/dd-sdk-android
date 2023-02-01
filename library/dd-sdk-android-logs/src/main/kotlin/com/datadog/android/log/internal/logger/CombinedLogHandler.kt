/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

internal class CombinedLogHandler(
    internal vararg val handlers: LogHandler
) : LogHandler {

    // region LogHandler

    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        handlers.forEach { it.handleLog(level, message, throwable, attributes, tags, timestamp) }
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
        handlers.forEach {
            it.handleLog(
                level,
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

    // endregion
}
