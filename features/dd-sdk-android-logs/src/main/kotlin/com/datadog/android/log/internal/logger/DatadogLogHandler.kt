/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.model.LogEvent
import android.util.Log as AndroidLog

internal class DatadogLogHandler(
    internal val loggerName: String,
    internal val logGenerator: LogGenerator,
    internal val sdkCore: FeatureSdkCore,
    internal val writer: DataWriter<LogEvent>,
    internal val attachNetworkInfo: Boolean,
    internal val bundleWithTraces: Boolean = true,
    internal val bundleWithRum: Boolean = true,
    internal val sampler: Sampler<Unit> = RateBasedSampler(DEFAULT_SAMPLE_RATE),
    internal val minLogPriority: Int = -1
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
        if (level < minLogPriority) {
            return
        }

        val resolvedTimeStamp = timestamp ?: System.currentTimeMillis()
        val combinedAttributes = mutableMapOf<String, Any?>()
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        if (logsFeature != null) {
            combinedAttributes.putAll(logsFeature.unwrap<LogsFeature>().getAttributes().toMutableMap())
        }
        combinedAttributes.putAll(attributes)
        if (sampler.sample(Unit)) {
            if (logsFeature != null) {
                val threadName = Thread.currentThread().name
                val withFeatureContexts = mutableSetOf<String>()
                if (bundleWithRum) withFeatureContexts.add(Feature.RUM_FEATURE_NAME)
                if (bundleWithTraces) withFeatureContexts.add(Feature.TRACING_FEATURE_NAME)
                logsFeature.withWriteContext(withFeatureContexts) { datadogContext, writeScope ->
                    val log = createLog(
                        level,
                        datadogContext,
                        message,
                        throwable,
                        combinedAttributes,
                        tags,
                        threadName,
                        resolvedTimeStamp
                    )
                    if (log != null) {
                        writeScope {
                            writer.write(it, log, EventType.DEFAULT)
                        }
                    }
                }
            } else {
                logLogsFeatureIsNotRegistered()
            }
        }

        if (level >= AndroidLog.ERROR) {
            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            if (rumFeature != null) {
                rumFeature.sendEvent(
                    mapOf(
                        "type" to "logger_error",
                        "message" to message,
                        "throwable" to throwable,
                        "attributes" to combinedAttributes
                    )
                )
            } else {
                logRumFeatureIsNotRegistered()
            }
        }
    }

    @Suppress("LongMethod")
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
        if (level < minLogPriority) {
            return
        }

        val resolvedTimeStamp = timestamp ?: System.currentTimeMillis()
        val combinedAttributes = mutableMapOf<String, Any?>()
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        if (logsFeature != null) {
            combinedAttributes.putAll(logsFeature.unwrap<LogsFeature>().getAttributes().toMutableMap())
        }
        combinedAttributes.putAll(attributes)

        if (sampler.sample(Unit)) {
            if (logsFeature != null) {
                val threadName = Thread.currentThread().name
                val withFeatureContexts = mutableSetOf<String>()
                if (bundleWithRum) withFeatureContexts.add(Feature.RUM_FEATURE_NAME)
                if (bundleWithTraces) withFeatureContexts.add(Feature.TRACING_FEATURE_NAME)
                logsFeature.withWriteContext(withFeatureContexts) { datadogContext, writeScope ->
                    val log = createLog(
                        level,
                        datadogContext,
                        message,
                        errorKind,
                        errorMessage,
                        errorStacktrace,
                        combinedAttributes,
                        tags,
                        threadName,
                        resolvedTimeStamp
                    )
                    if (log != null) {
                        writeScope {
                            writer.write(it, log, EventType.DEFAULT)
                        }
                    }
                }
            } else {
                logLogsFeatureIsNotRegistered()
            }
        }

        if (level >= AndroidLog.ERROR) {
            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            if (rumFeature != null) {
                rumFeature.sendEvent(
                    mapOf(
                        "type" to "logger_error_with_stacktrace",
                        "message" to message,
                        "stacktrace" to errorStacktrace,
                        "attributes" to combinedAttributes
                    )
                )
            } else {
                logRumFeatureIsNotRegistered()
            }
        }
    }

    // endregion

    // region Internal

    @Suppress("LongParameterList")
    private fun createLog(
        level: Int,
        datadogContext: DatadogContext,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        threadName: String,
        timestamp: Long
    ): LogEvent? {
        return logGenerator.generateLog(
            level,
            message,
            throwable,
            attributes,
            tags,
            timestamp,
            datadogContext = datadogContext,
            attachNetworkInfo = attachNetworkInfo,
            loggerName = loggerName,
            threadName = threadName,
            bundleWithRum = bundleWithRum,
            bundleWithTraces = bundleWithTraces
        )
    }

    @Suppress("LongParameterList")
    private fun createLog(
        level: Int,
        datadogContext: DatadogContext,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStack: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        threadName: String,
        timestamp: Long
    ): LogEvent? {
        return logGenerator.generateLog(
            level,
            message,
            errorKind,
            errorMessage,
            errorStack,
            attributes,
            tags,
            timestamp,
            datadogContext = datadogContext,
            attachNetworkInfo = attachNetworkInfo,
            loggerName = loggerName,
            threadName = threadName,
            bundleWithRum = bundleWithRum,
            bundleWithTraces = bundleWithTraces
        )
    }

    private fun logLogsFeatureIsNotRegistered() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { LOGS_FEATURE_NOT_REGISTERED }
        )
    }

    private fun logRumFeatureIsNotRegistered() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            { RUM_FEATURE_NOT_REGISTERED }
        )
    }

    // endregion

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 100f
        const val LOGS_FEATURE_NOT_REGISTERED =
            "Requested to write log, but Logs feature is not registered."
        const val RUM_FEATURE_NOT_REGISTERED =
            "Requested to forward error log to RUM, but RUM feature is not registered."
    }
}
