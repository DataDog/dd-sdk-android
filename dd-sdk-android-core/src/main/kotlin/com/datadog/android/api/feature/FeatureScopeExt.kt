/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.lint.InternalApi

/**
 * Measures the execution time for the given block and report it as a MethodCall telemetry metric.
 * @param R the type of the result of the operation
 * @param callerClass the class calling the measured method
 * @param operationName the operationName to report in the metric
 * @param samplingRate the sampling rate for the metric
 * @param operation the operation to report
 */
@InternalApi
fun <R : Any?> InternalLogger.measureMethodCallPerf(
    callerClass: Class<*>,
    operationName: String,
    samplingRate: Float = 100f,
    operation: () -> R
): R {
    val metric = startPerformanceMeasure(
        callerClass = callerClass.name,
        metric = TelemetryMetricType.MethodCalled,
        samplingRate = samplingRate,
        operationName = operationName
    )

    val result = operation()

    val isSuccessful = (result != null) && ((result !is Collection<*>) || result.isNotEmpty())
    metric?.stopAndSend(isSuccessful)

    return result
}
