/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.PerformanceMetric.Companion.METRIC_TYPE
import com.datadog.android.internal.time.TimeProvider

/**
 * Performance metric to measure the execution time for a method.
 * @param internalLogger - an instance of the internal logger.
 * @param operationName the operation name
 * @param callerClass - the class calling the performance metric.
 * @param creationSampleRate - sampling frequency used to create the metric
 * @param timeProvider - the provider for time measurements.
 */
internal class MethodCalledTelemetry(
    internal val internalLogger: InternalLogger,
    internal val operationName: String,
    internal val callerClass: String,
    internal val creationSampleRate: Float,
    internal val timeProvider: TimeProvider
) : PerformanceMetric {

    internal val startTime: Long = timeProvider.getDeviceElapsedTimeNanos()

    override fun stopAndSend(isSuccessful: Boolean) {
        val executionTime = timeProvider.getDeviceElapsedTimeNanos() - startTime
        val additionalProperties: MutableMap<String, Any> = mutableMapOf()

        additionalProperties[EXECUTION_TIME] = executionTime
        additionalProperties[OPERATION_NAME] = operationName
        additionalProperties[CALLER_CLASS] = callerClass
        additionalProperties[IS_SUCCESSFUL] = isSuccessful
        additionalProperties[METRIC_TYPE] = METRIC_TYPE_VALUE

        internalLogger.logMetric(
            messageBuilder = { METHOD_CALLED_METRIC_NAME },
            additionalProperties = additionalProperties,
            samplingRate = MethodCallSamplingRate.ALL.rate, // sampling is performed on start
            creationSampleRate = creationSampleRate
        )
    }

    companion object {
        /**
         * Title of the metric to be sent.
         */
        const val METHOD_CALLED_METRIC_NAME: String = "[Mobile Metric] Method Called"

        /**
         * Metric type value.
         */
        const val METRIC_TYPE_VALUE: String = "method called"

        /**
         * The key for operation name.
         */
        const val OPERATION_NAME: String = "operation_name"

        /**
         * The key for caller class.
         */
        const val CALLER_CLASS: String = "caller_class"

        /**
         * The key for is successful.
         */
        const val IS_SUCCESSFUL: String = "is_successful"

        /**
         * The key for execution time.
         */
        const val EXECUTION_TIME: String = "execution_time"
    }
}
