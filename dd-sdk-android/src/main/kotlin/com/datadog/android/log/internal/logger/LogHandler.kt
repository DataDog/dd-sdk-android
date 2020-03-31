/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

internal interface LogHandler {

    /**
     * Handle the log.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exists in this logger, it will be overridden (just for this message)
     */
    fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap(),
        tags: Set<String> = emptySet(),
        timestamp: Long? = null
    )
}
