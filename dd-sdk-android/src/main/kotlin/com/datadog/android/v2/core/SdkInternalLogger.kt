/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.util.Log
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.internal.utils.telemetry
import com.datadog.android.v2.api.InternalLogger

internal object SdkInternalLogger : InternalLogger {

    // region InternalLogger

    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        message: String,
        error: Throwable?,
        attributes: Map<String, Any?>
    ) {
        when (target) {
            // TODO RUMM-2764 we should remove sdkLogger, devLogger and telemetry
            //  and use only this instance for logging
            InternalLogger.Target.USER -> logToUser(level, message, error, attributes)
            InternalLogger.Target.MAINTAINER -> logToMaintainer(level, message, error, attributes)
            InternalLogger.Target.TELEMETRY -> logToTelemetry(level, message, error)
        }
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        message: String,
        error: Throwable?,
        attributes: Map<String, Any?>
    ) {
        targets.forEach {
            log(level, it, message, error, attributes)
        }
    }

    // endregion

    // region Internal

    private fun logToUser(
        level: InternalLogger.Level,
        message: String,
        error: Throwable?,
        attributes: Map<String, Any?>
    ) {
        devLogger.log(
            level.toLogLevel(),
            message,
            error,
            attributes
        )
    }

    private fun logToMaintainer(
        level: InternalLogger.Level,
        message: String,
        error: Throwable?,
        attributes: Map<String, Any?>
    ) {
        sdkLogger.log(
            level.toLogLevel(),
            message,
            error,
            attributes
        )
    }

    private fun logToTelemetry(
        level: InternalLogger.Level,
        message: String,
        error: Throwable?
    ) {
        if (level == InternalLogger.Level.ERROR ||
            level == InternalLogger.Level.WARN ||
            error != null
        ) {
            telemetry.error(message, error)
        } else {
            telemetry.debug(message)
        }
    }

    private fun InternalLogger.Level.toLogLevel(): Int {
        return when (this) {
            InternalLogger.Level.DEBUG -> Log.DEBUG
            InternalLogger.Level.INFO -> Log.INFO
            InternalLogger.Level.WARN -> Log.WARN
            InternalLogger.Level.ERROR -> Log.ERROR
        }
    }

    // endregion
}
