/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MetricBase.Companion.METRIC_TYPE

internal class MethodCalledTelemetry(
    private val callerClass: String,
    private val logger: InternalLogger,
    private val startTime: Long = System.nanoTime()
) : MetricBase {

    override fun sendMetric(isSuccessful: Boolean) {
        val executionTime = System.nanoTime() - startTime
        val additionalProperties: MutableMap<String, Any> = mutableMapOf()

        additionalProperties[EXECUTION_TIME] = executionTime
        additionalProperties[OPERATION_NAME] = METHOD_CALL_OPERATION_NAME
        additionalProperties[CALLER_CLASS] = callerClass
        additionalProperties[IS_SUCCESSFUL] = isSuccessful
        additionalProperties[METRIC_TYPE] = METRIC_TYPE_VALUE

        logger.logMetric(
            messageBuilder = { METHOD_CALLED_METRIC_NAME },
            additionalProperties = additionalProperties
        )
    }

    internal companion object {
        // Title of the metric to be sent
        internal const val METHOD_CALLED_METRIC_NAME = "[Mobile Metric] Method Called"

        // Metric type value.
        internal const val METRIC_TYPE_VALUE = "method called"

        // The key for operation name.
        internal const val OPERATION_NAME = "operation_name"

        // The key for caller class.
        internal const val CALLER_CLASS = "caller_class"

        // The key for is successful.
        internal const val IS_SUCCESSFUL = "is_successful"

        // The key for execution time.
        internal const val EXECUTION_TIME = "execution_time"

        // The value for operation name
        internal const val METHOD_CALL_OPERATION_NAME = "Capture Record"
    }
}
