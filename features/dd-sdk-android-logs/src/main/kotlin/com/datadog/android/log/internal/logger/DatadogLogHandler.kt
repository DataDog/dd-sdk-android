/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.storage.DataWriter
import android.util.Log as AndroidLog

internal class DatadogLogHandler(
    internal val loggerName: String,
    internal val logGenerator: LogGenerator,
    internal val sdkCore: SdkCore,
    internal val writer: DataWriter<LogEvent>,
    internal val attachNetworkInfo: Boolean,
    internal val bundleWithTraces: Boolean = true,
    internal val bundleWithRum: Boolean = true,
    internal val sampler: Sampler = RateBasedSampler(DEFAULT_SAMPLE_RATE),
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
        if (sampler.sample()) {
            val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
            if (logsFeature != null) {
                val threadName = Thread.currentThread().name
                logsFeature.withWriteContext { datadogContext, eventBatchWriter ->
                    val log = createLog(
                        level,
                        datadogContext,
                        message,
                        throwable,
                        attributes,
                        tags,
                        threadName,
                        resolvedTimeStamp
                    )
                    if (log != null) {
                        @Suppress("ThreadSafety") // called in a worker thread context
                        writer.write(eventBatchWriter, log)
                    }
                }
            } else {
                sdkCore._internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    "Requested to write log, but Logs feature is not registered."
                )
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
                        "attributes" to attributes
                    )
                )
            } else {
                sdkCore._internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    RUM_FEATURE_NOT_REGISTERED_FOR_ERROR_FORWARD_INFO
                )
            }
        }
    }

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
        if (sampler.sample()) {
            val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
            if (logsFeature != null) {
                val threadName = Thread.currentThread().name
                logsFeature.withWriteContext { datadogContext, eventBatchWriter ->
                    val log = createLog(
                        level,
                        datadogContext,
                        message,
                        errorKind,
                        errorMessage,
                        errorStacktrace,
                        attributes,
                        tags,
                        threadName,
                        resolvedTimeStamp
                    )
                    if (log != null) {
                        @Suppress("ThreadSafety") // called in a worker thread context
                        writer.write(eventBatchWriter, log)
                    }
                }
            } else {
                sdkCore._internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    LOGS_FEATURE_NOT_REGISTERED_INFO
                )
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
                        "attributes" to attributes
                    )
                )
            } else {
                sdkCore._internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    RUM_FEATURE_NOT_REGISTERED_FOR_ERROR_FORWARD_INFO
                )
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

    // endregion

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 100f
        const val LOGS_FEATURE_NOT_REGISTERED_INFO =
            "Requested to write log, but Logs feature is not registered."
        const val RUM_FEATURE_NOT_REGISTERED_FOR_ERROR_FORWARD_INFO =
            "RUM feature is not registered, won't forward error log to RUM."
    }
}
