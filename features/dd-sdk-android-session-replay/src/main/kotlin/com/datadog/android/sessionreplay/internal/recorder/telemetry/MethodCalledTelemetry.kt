/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MetricBase.Companion.METRIC_TYPE

internal class MethodCalledTelemetry(
    private val callerClass: String,
    private val logger: InternalLogger,
    private val startTime: Long = System.nanoTime(),
    private val internalSdkCore: InternalSdkCore? = Datadog.getInstance() as? InternalSdkCore
) : MetricBase {
    internal fun stopMethodCalled(isSuccessful: Boolean) {
        val executionTime = System.nanoTime() - startTime
        val additionalProperties: MutableMap<String, Any> = mutableMapOf()

        val deviceInfo = internalSdkCore?.getDatadogContext()?.deviceInfo

        additionalProperties[EXECUTION_TIME] = executionTime
        additionalProperties[OPERATION_NAME] = METHOD_CALL_OPERATION_NAME
        additionalProperties[CALLER_CLASS] = callerClass
        additionalProperties[IS_SUCCESSFUL] = isSuccessful
        additionalProperties[METRIC_TYPE] = METRIC_TYPE_VALUE
        val deviceMap = mutableMapOf<String, Any>()
        val osMap = mutableMapOf<String, Any>()

        deviceInfo?.deviceModel?.let {
            deviceMap[DEVICE_MODEL] = it
        }

        deviceInfo?.deviceBrand?.let {
            deviceMap[DEVICE_BRAND] = it
        }

        deviceInfo?.architecture?.let {
            deviceMap[DEVICE_ARCHITECTURE] = it
        }

        deviceInfo?.osName?.let {
            osMap[OS_NAME] = it
        }

        deviceInfo?.osVersion?.let {
            osMap[OS_VERSION] = it
        }

        deviceInfo?.deviceBuildId?.let {
            osMap[OS_BUILD] = it
        }

        additionalProperties[DEVICE_KEY] = deviceMap
        additionalProperties[OS_KEY] = osMap

        logger.logMetric(
            messageBuilder = { METRIC_NAME },
            additionalProperties = additionalProperties
        )
    }

    internal companion object {
        // Title of the metric to be sent
        internal const val METRIC_NAME = "[Mobile Metric] Method Called"

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

        // The key for device object.
        internal const val DEVICE_KEY = "device"

        // The key for device model name.
        internal const val DEVICE_MODEL = "model"

        // The key for device brand.
        internal const val DEVICE_BRAND = "brand"

        // The key for CPU architecture.
        internal const val DEVICE_ARCHITECTURE = "architecture"

        // The key for operating system object.
        internal const val OS_KEY = "os"

        // The key for OS name.
        internal const val OS_NAME = "name"

        // The key for OS version.
        internal const val OS_VERSION = "version"

        // The key for OS build.
        internal const val OS_BUILD = "build"

        // The value for operation name
        internal const val METHOD_CALL_OPERATION_NAME = "Capture Record"
    }
}
