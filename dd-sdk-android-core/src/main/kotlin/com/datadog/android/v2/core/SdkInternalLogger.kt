/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger

internal class SdkInternalLogger(
    private val sdkCore: FeatureSdkCore?,
    devLogHandlerFactory: () -> LogcatLogHandler = {
        LogcatLogHandler(DEV_LOG_TAG) { level ->
            level >= Datadog.getVerbosity()
        }
    },
    sdkLogHandlerFactory: () -> LogcatLogHandler? = {
        if (BuildConfig.LOGCAT_ENABLED) {
            LogcatLogHandler(SDK_LOG_TAG)
        } else {
            null
        }
    }
) : InternalLogger {

    /**
     * Global Dev Logger. This logger is meant for user's debugging purposes.
     * Logcat logs are conditioned by the [Datadog.libraryVerbosity].
     * No Datadog logs should be sent.
     */
    internal val devLogger = devLogHandlerFactory.invoke()

    /**
     * Global SDK Logger. This logger is meant for internal debugging purposes.
     * Logcat logs are conditioned by a BuildConfig flag (set to false for releases).
     */
    internal val sdkLogger = sdkLogHandlerFactory.invoke()

    // region InternalLogger

    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        message: String,
        throwable: Throwable?
    ) {
        when (target) {
            InternalLogger.Target.USER -> logToUser(level, message, throwable)
            InternalLogger.Target.MAINTAINER -> logToMaintainer(level, message, throwable)
            InternalLogger.Target.TELEMETRY -> logToTelemetry(level, message, throwable)
        }
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        message: String,
        throwable: Throwable?
    ) {
        targets.forEach {
            log(level, it, message, throwable)
        }
    }

    // endregion

    // region Internal

    private fun logToUser(
        level: InternalLogger.Level,
        message: String,
        error: Throwable?
    ) {
        devLogger.log(
            level.toLogLevel(),
            message.withSdkName(),
            error
        )
    }

    private fun logToMaintainer(
        level: InternalLogger.Level,
        message: String,
        error: Throwable?
    ) {
        sdkLogger?.log(
            level.toLogLevel(),
            message.withSdkName(),
            error
        )
    }

    private fun logToTelemetry(
        level: InternalLogger.Level,
        message: String,
        error: Throwable?
    ) {
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        val telemetryEvent = if (
            level == InternalLogger.Level.ERROR ||
            level == InternalLogger.Level.WARN ||
            error != null
        ) {
            mapOf(
                "type" to "telemetry_error",
                "message" to message,
                "throwable" to error
            )
        } else {
            mapOf(
                "type" to "telemetry_debug",
                "message" to message
            )
        }
        rumFeature.sendEvent(telemetryEvent)
    }

    private fun InternalLogger.Level.toLogLevel(): Int {
        return when (this) {
            InternalLogger.Level.VERBOSE -> Log.VERBOSE
            InternalLogger.Level.DEBUG -> Log.DEBUG
            InternalLogger.Level.INFO -> Log.INFO
            InternalLogger.Level.WARN -> Log.WARN
            InternalLogger.Level.ERROR -> Log.ERROR
        }
    }

    private fun String.withSdkName(): String {
        val instanceName = sdkCore?.name
        return if (instanceName != null) {
            "[$instanceName]: $this"
        } else {
            this
        }
    }

    companion object {
        internal const val SDK_LOG_TAG = "DD_LOG"
        internal const val DEV_LOG_TAG = "Datadog"
    }

    // endregion
}
