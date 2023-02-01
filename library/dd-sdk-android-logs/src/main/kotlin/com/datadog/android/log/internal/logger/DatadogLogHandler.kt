/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.storage.DataWriter
import android.util.Log as AndroidLog

internal class DatadogLogHandler(
    internal val loggerName: String,
    internal val logGenerator: LogGenerator,
    internal val sdkCore: SdkCore,
    internal val writer: DataWriter<LogEvent>,
    internal val attachNetworkInfo: Boolean,
    internal val bundleWithTraces: Boolean = true,
    internal val bundleWithRum: Boolean = true,
    internal val sampler: Sampler = RateBasedSampler(1.0f),
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
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    "Requested to write log, but Logs feature is not registered."
                )
            }
        }

        if (level >= AndroidLog.ERROR) {
            GlobalRum.get().addError(message, RumErrorSource.LOGGER, throwable, attributes)
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
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    "Requested to write log, but Logs feature is not registered."
                )
            }
        }

        if (level >= AndroidLog.ERROR) {
            GlobalRum.get()
                .addErrorWithStacktrace(message, RumErrorSource.LOGGER, errorStacktrace, attributes)
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
}
