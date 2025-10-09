/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.internal.telemetry.InternalTelemetryEvent

class FakeInternalLogger : InternalLogger {

    var lastMetric: Pair<String, Map<String, Any?>>? = null

    var errorLog: String? = null

    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?,
        force: Boolean
    ) {
        errorLog = messageBuilder()
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?,
        force: Boolean
    ) {
        // do nothing
    }

    override fun logMetric(
        messageBuilder: () -> String,
        additionalProperties: Map<String, Any?>,
        samplingRate: Float,
        creationSampleRate: Float?
    ) {
        lastMetric = Pair(messageBuilder(), additionalProperties)
    }

    override fun startPerformanceMeasure(
        callerClass: String,
        metric: TelemetryMetricType,
        samplingRate: Float,
        operationName: String
    ): PerformanceMetric? {
        // do nothing
        return null
    }

    override fun logApiUsage(
        samplingRate: Float,
        apiUsageEventBuilder: () -> InternalTelemetryEvent.ApiUsage
    ) {
        // do nothing
    }
}
