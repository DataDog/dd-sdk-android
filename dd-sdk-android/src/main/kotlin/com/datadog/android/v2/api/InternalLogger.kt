/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.tools.annotation.NoOpImplementation

/**
 * A Logger used to log messages from the internal implementation of the Datadog SDKs.
 */
@NoOpImplementation
interface InternalLogger {

    /**
     * The severity level of a logged message.
     */
    enum class Level {
        DEBUG,
        INFO,
        WARN,
        ERROR;
    }

    /**
     * The target handler for a log message.
     */
    enum class Target {
        USER,
        MAINTAINER,
        TELEMETRY
    }

    /**
     * Logs a message from the internal implementation.
     * @param level the severity level of the log
     * @param target the target handler for the log
     * @param message the log message
     * @param error an optional throwable error
     * @param attributes an optional map of custom attributes
     */
    fun log(
        level: Level,
        target: Target,
        message: String,
        error: Throwable?,
        attributes: Map<String, Any?>
    )

    /**
     * Logs a message from the internal implementation.
     * @param level the severity level of the log
     * @param targets list of the target handlers for the log
     * @param message the log message
     * @param error an optional throwable error
     * @param attributes an optional map of custom attributes
     */
    fun log(
        level: Level,
        targets: List<Target>,
        message: String,
        error: Throwable?,
        attributes: Map<String, Any?>
    )
}
