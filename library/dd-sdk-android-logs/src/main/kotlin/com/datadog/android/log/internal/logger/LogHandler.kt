/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface LogHandler {

    /**
     * Handle the log.
     * @param level the priority level (must be one of the Android Log.* constants)
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exists in this logger, it will be overridden (just for this message)
     * @param tags the tags for this message
     * @param timestamp the time at which this log occurred
     */
    fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap(),
        tags: Set<String> = emptySet(),
        timestamp: Long? = null
    )

    /**
     * Handle the log.
     * @param level the priority level (must be one of the Android Log.* constants)
     * @param message the message to be logged
     * @param errorKind the kind of error to be logged with the message
     * @param errorMessage the message from the error to be logged with this message
     * @param errorStacktrace the stack trace from the error to be logged with this message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exists in this logger, it will be overridden (just for this message)
     * @param tags the tags for this message
     * @param timestamp the time at which this log occurred
     */
    fun handleLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStacktrace: String?,
        attributes: Map<String, Any?> = emptyMap(),
        tags: Set<String> = emptySet(),
        timestamp: Long? = null
    )
}
