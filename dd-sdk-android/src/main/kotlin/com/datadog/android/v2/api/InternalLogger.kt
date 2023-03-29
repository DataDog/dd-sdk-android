/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.v2.core.SdkInternalLogger
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
        VERBOSE,
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
     * @param throwable an optional throwable error
     */
    fun log(
        level: Level,
        target: Target,
        message: String,
        throwable: Throwable? = null
    )

    /**
     * Logs a message from the internal implementation.
     * @param level the severity level of the log
     * @param targets list of the target handlers for the log
     * @param message the log message
     * @param throwable an optional throwable error
     */
    fun log(
        level: Level,
        targets: List<Target>,
        message: String,
        throwable: Throwable? = null
    )

    companion object {

        /**
         * Logger for the cases when SDK instance is not yet available. Try to use the logger
         * provided by [SdkCore._internalLogger] instead if possible.
         */
        val UNBOUND: InternalLogger = SdkInternalLogger(null)
    }
}
