/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.logger

import android.util.Log
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.metrics.MethodCalledTelemetry
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.internal.attributes.LocalAttribute
import com.datadog.android.internal.attributes.enrichWithNonNullAttribute
import com.datadog.android.internal.telemetry.InternalTelemetryEvent

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

    override fun logMetric(
        messageBuilder: () -> String,
        additionalProperties: Map<String, Any?>,
        samplingRate: Float,
        creationSampleRate: Float?
    ) {
        if (!sample(samplingRate)) return
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        val additionalPropertiesMutable = additionalProperties.toMutableMap()
            .enrichWithNonNullAttribute(
                LocalAttribute.Key.CREATION_SAMPLING_RATE,
                creationSampleRate
            )
            .enrichWithNonNullAttribute(
                LocalAttribute.Key.REPORTING_SAMPLING_RATE,
                samplingRate
            )

        val metricEvent = InternalTelemetryEvent.Metric(
            message = messageBuilder(),
            additionalProperties = additionalPropertiesMutable
        )
        rumFeature.sendEvent(metricEvent)
    }

    override fun startPerformanceMeasure(
        callerClass: String,
        metric: TelemetryMetricType,
        samplingRate: Float,
        operationName: String
    ): PerformanceMetric? {
        if (!sample(samplingRate)) return null

        return when (metric) {
            TelemetryMetricType.MethodCalled -> {
                MethodCalledTelemetry(
                    internalLogger = this,
                    operationName = operationName,
                    callerClass = callerClass,
                    creationSampleRate = samplingRate
                )
            }
        }
    }

    override fun logApiUsage(
        samplingRate: Float,
        apiUsageEventBuilder: () -> InternalTelemetryEvent.ApiUsage
    ) {
        if (!sample(samplingRate)) return
        val rumFeature = sdkCore?.getFeature(Feature.RUM_FEATURE_NAME) ?: return

        val event = apiUsageEventBuilder()
        event.additionalProperties.enrichWithNonNullAttribute(
            LocalAttribute.Key.REPORTING_SAMPLING_RATE,
            samplingRate
        )

        rumFeature.sendEvent(event)
    }

    // endregion

    // region Internal

    fun sample(samplingRate: Float): Boolean {
        return RateBasedSampler<Unit>(samplingRate).sample(Unit)
    }

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
            InternalTelemetryEvent.Log.Error(
                message = message,
                additionalProperties = additionalProperties,
                error = error
            )
        } else {
            InternalTelemetryEvent.Log.Debug(
                message = message,
                additionalProperties = additionalProperties
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
