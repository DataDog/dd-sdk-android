/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore

internal class SdkInternalLogger(
    private val sdkCore: FeatureSdkCore?,
    userLogHandlerFactory: () -> LogcatLogHandler = {
        LogcatLogHandler(DEV_LOG_TAG) { level ->
            level >= Datadog.getVerbosity()
        }
    },
    maintainerLogHandlerFactory: () -> LogcatLogHandler? = {
        if (BuildConfig.LOGCAT_ENABLED) {
            LogcatLogHandler(SDK_LOG_TAG)
        } else {
            null
        }
    }
) : InternalLogger {

    /**
     * This logger is meant for user's debugging purposes.
     * Logcat logs are conditioned by the [Datadog.libraryVerbosity].
     * No Datadog logs should be sent.
     */
    internal val userLogger = userLogHandlerFactory.invoke()

    /**
     * This logger is meant for internal debugging purposes.
     * Logcat logs are conditioned by a BuildConfig flag (set to false for releases).
     */
    internal val maintainerLogger = maintainerLogHandlerFactory.invoke()

    private val onlyOnceUserMessages = mutableSetOf<String>()
    private val onlyOnceMaintainerMessages = mutableSetOf<String>()
    private val onlyOnceTelemetryMessages = mutableSetOf<String>()

    // region InternalLogger

    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        when (target) {
            InternalLogger.Target.USER -> logToUser(level, messageBuilder, throwable, onlyOnce)
            InternalLogger.Target.MAINTAINER -> logToMaintainer(
                level,
                messageBuilder,
                throwable,
                onlyOnce
            )

            InternalLogger.Target.TELEMETRY -> logToTelemetry(
                level,
                messageBuilder,
                throwable,
                onlyOnce,
                additionalProperties
            )
        }
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        targets.forEach {
            log(level, it, messageBuilder, throwable, onlyOnce, additionalProperties)
        }
    }

    override fun logMetric(messageBuilder: () -> String, additionalProperties: Map<String, Any?>) {
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        val message = messageBuilder()
        val telemetryEvent =
            mapOf(
                TYPE_KEY to "mobile_metric",
                MESSAGE_KEY to message,
                ADDITIONAL_PROPERTIES_KEY to additionalProperties
            )
        rumFeature.sendEvent(telemetryEvent)
    }

    // endregion

    // region Internal

    private fun logToUser(
        level: InternalLogger.Level,
        messageBuilder: () -> String,
        error: Throwable?,
        onlyOnce: Boolean
    ) {
        sendToLogHandler(
            userLogger,
            level,
            messageBuilder,
            error,
            onlyOnce,
            onlyOnceUserMessages
        )
    }

    private fun logToMaintainer(
        level: InternalLogger.Level,
        messageBuilder: () -> String,
        error: Throwable?,
        onlyOnce: Boolean
    ) {
        maintainerLogger?.let {
            sendToLogHandler(
                it,
                level,
                messageBuilder,
                error,
                onlyOnce,
                onlyOnceMaintainerMessages
            )
        }
    }

    private fun sendToLogHandler(
        handler: LogcatLogHandler,
        level: InternalLogger.Level,
        messageBuilder: () -> String,
        error: Throwable?,
        onlyOnce: Boolean,
        knownSingleMessages: MutableSet<String>
    ) {
        if (!handler.canLog(level.toLogLevel())) return
        val message = messageBuilder().withSdkName()
        if (onlyOnce) {
            if (knownSingleMessages.contains(message)) {
                // drop the message… wait should we log that we dropped it?
                return
            } else {
                knownSingleMessages.add(message)
            }
        }
        handler.log(level.toLogLevel(), message, error)
    }

    private fun logToTelemetry(
        level: InternalLogger.Level,
        messageBuilder: () -> String,
        error: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        val message = messageBuilder()
        if (onlyOnce) {
            if (onlyOnceTelemetryMessages.contains(message)) {
                // drop the message… wait should we log that we dropped it?
                return
            } else {
                onlyOnceTelemetryMessages.add(message)
            }
        }
        val telemetryEvent = if (
            level == InternalLogger.Level.ERROR ||
            level == InternalLogger.Level.WARN ||
            error != null
        ) {
            mutableMapOf<String, Any?>(
                TYPE_KEY to "telemetry_error",
                MESSAGE_KEY to message,
                THROWABLE_KEY to error
            ).apply {
                if (!additionalProperties.isNullOrEmpty()) {
                    put(ADDITIONAL_PROPERTIES_KEY, additionalProperties)
                }
            }
        } else {
            mutableMapOf<String, Any?>(
                TYPE_KEY to "telemetry_debug",
                MESSAGE_KEY to message
            ).apply {
                if (!additionalProperties.isNullOrEmpty()) {
                    put(ADDITIONAL_PROPERTIES_KEY, additionalProperties)
                }
            }
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
        private const val MESSAGE_KEY = "message"
        private const val TYPE_KEY = "type"
        private const val THROWABLE_KEY = "throwable"
        private const val ADDITIONAL_PROPERTIES_KEY = "additionalProperties"
    }

    // endregion
}
