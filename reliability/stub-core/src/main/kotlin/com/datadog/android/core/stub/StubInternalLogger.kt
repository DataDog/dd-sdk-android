/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.internal.telemetry.InternalTelemetryEvent

@Suppress("UnsafeThirdPartyFunctionCall")
internal class StubInternalLogger : InternalLogger {

    val telemetryEventsWritten = mutableListOf<StubTelemetryEvent>()

    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        val message = messageBuilder()
        println("${level.name.first()} [${target.name.first()}]: $message")
        additionalProperties?.log()
        throwable?.printStackTrace()

        if (target == InternalLogger.Target.TELEMETRY) {
            val telemetryEvent = StubTelemetryEvent(
                type = StubTelemetryEvent.Type.LOG,
                message = message,
                additionalProperties = additionalProperties.orEmpty(),
                level = level,
                samplingRate = if (onlyOnce) -1f else 100f
            )
            telemetryEventsWritten.add(telemetryEvent)
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
        val message = messageBuilder()
        println("${level.name.first()} [${targets.joinToString { it.name.first().toString() }}]: $message")
        additionalProperties?.log()
        throwable?.printStackTrace()
        if (InternalLogger.Target.TELEMETRY in targets) {
            val telemetryEvent = StubTelemetryEvent(
                type = StubTelemetryEvent.Type.LOG,
                message = message,
                additionalProperties = additionalProperties.orEmpty(),
                level = level
            )
            telemetryEventsWritten.add(telemetryEvent)
        }
    }

    override fun logMetric(
        messageBuilder: () -> String,
        additionalProperties: Map<String, Any?>,
        samplingRate: Float,
        creationSampleRate: Float?
    ) {
        println("M [T]: ${messageBuilder()} | $samplingRate%")
        additionalProperties.log()
        val message = messageBuilder()
        val telemetryEvent = StubTelemetryEvent(
            type = StubTelemetryEvent.Type.METRIC,
            message = message,
            additionalProperties = additionalProperties,
            samplingRate = samplingRate,
            creationSampleRate = creationSampleRate
        )
        telemetryEventsWritten.add(telemetryEvent)
    }

    override fun startPerformanceMeasure(
        callerClass: String,
        metric: TelemetryMetricType,
        samplingRate: Float,
        operationName: String
    ): PerformanceMetric? {
        println("P [T]: $operationName ($callerClass)")
        return null
    }

    override fun logApiUsage(
        samplingRate: Float,
        apiUsageEventBuilder: () -> InternalTelemetryEvent.ApiUsage
    ) {
        val apiUsageEvent = apiUsageEventBuilder()
        println("U [T]: ${apiUsageEvent.javaClass.simpleName} | $samplingRate%")
        apiUsageEvent.additionalProperties.log()
        val telemetryEvent = StubTelemetryEvent(
            type = StubTelemetryEvent.Type.API_USAGE,
            message = apiUsageEvent.javaClass.name,
            additionalProperties = apiUsageEvent.additionalProperties,
            samplingRate = samplingRate
        )
        telemetryEventsWritten.add(telemetryEvent)
    }

    private fun <K, T> Map<K, T>.log() {
        forEach {
            println("    ${it.key}: ${it.value}")
        }
    }
}
