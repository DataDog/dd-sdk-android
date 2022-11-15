/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import android.util.Log as AndroidLog

internal class DatadogLogHandler(
    internal val logGenerator: LogGenerator,
    internal val writer: DataWriter<LogEvent>,
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
            val log = createLog(level, message, throwable, attributes, tags, resolvedTimeStamp)
            writer.write(log)
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
            val log = createLog(
                level,
                message,
                errorKind,
                errorMessage,
                errorStacktrace,
                attributes,
                tags,
                resolvedTimeStamp
            )
            writer.write(log)
        }

        if (level >= AndroidLog.ERROR) {
            GlobalRum.get().addErrorWithStacktrace(message, RumErrorSource.LOGGER, errorStacktrace, attributes)
        }
    }

    // endregion

    // region Internal

    @Suppress("LongParameterList")
    private fun createLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long
    ): LogEvent {
        return logGenerator.generateLog(
            level,
            message,
            throwable,
            attributes,
            tags,
            timestamp,
            bundleWithRum = bundleWithRum,
            bundleWithTraces = bundleWithTraces
        )
    }

    @Suppress("LongParameterList")
    private fun createLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStack: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long
    ): LogEvent {
        return logGenerator.generateLog(
            level,
            message,
            errorKind,
            errorMessage,
            errorStack,
            attributes,
            tags,
            timestamp,
            bundleWithRum = bundleWithRum,
            bundleWithTraces = bundleWithTraces
        )
    }

    // endregion
}
