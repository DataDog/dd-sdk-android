/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

internal class ConditionalLogHandler(
    internal val delegateHandler: LogHandler,
    internal val condition: (Int, Throwable?) -> Boolean
) : LogHandler {
    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        if (condition(level, throwable)) {
            delegateHandler.handleLog(level, message, throwable, attributes, tags, timestamp)
        }
    }
}
