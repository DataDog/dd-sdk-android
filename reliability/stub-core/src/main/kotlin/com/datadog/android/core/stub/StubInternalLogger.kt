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

    val telemetryEventsWritten = mutableListOf<Map<String, Any>>()
    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        println("${level.name.first()} [${target.name.first()}]: ${messageBuilder()}")
        additionalProperties?.log()
        throwable?.printStackTrace()
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        println("${level.name.first()} [${targets.joinToString { it.name.first().toString() }}]: ${messageBuilder()}")
        additionalProperties?.log()
        throwable?.printStackTrace()
    }

    override fun logMetric(messageBuilder: () -> String, additionalProperties: Map<String, Any?>, samplingRate: Float) {
        println("M [T]: ${messageBuilder()} | $samplingRate%")
        additionalProperties.log()
        val message = messageBuilder()
        val telemetryEvent =
            mapOf(
                "type" to "mobile_metric",
                "message" to message,
                "additionalProperties" to additionalProperties
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
        apiUsageEvent: InternalTelemetryEvent.ApiUsage,
        samplingRate: Float
    ) {
        println("${apiUsageEvent::class.simpleName} | $samplingRate%")
        apiUsageEvent.additionalProperties.log()
    }

    private fun <K, T> Map<K, T>.log() {
        forEach {
            println("    ${it.key}: ${it.value}")
        }
    }
}
