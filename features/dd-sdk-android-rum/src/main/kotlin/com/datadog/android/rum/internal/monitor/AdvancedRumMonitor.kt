/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.internal.debug.RumDebugListener
import com.datadog.android.telemetry.internal.TelemetryCoreConfiguration
import com.datadog.tools.annotation.NoOpImplementation

/**
 * FOR INTERNAL USAGE ONLY.
 */
@SuppressWarnings("ComplexInterface", "TooManyFunctions")
@NoOpImplementation
internal interface AdvancedRumMonitor : RumMonitor, AdvancedNetworkRumMonitor {

    fun resetSession()

    fun start()

    fun sendWebViewEvent()

    fun addLongTask(durationNs: Long, target: String)

    fun addCrash(
        message: String,
        source: RumErrorSource,
        throwable: Throwable,
        threads: List<ThreadDump>
    )

    fun eventSent(viewId: String, event: StorageEvent)

    fun eventDropped(viewId: String, event: StorageEvent)

    fun setDebugListener(listener: RumDebugListener?)

    fun sendDebugTelemetryEvent(
        message: String,
        additionalProperties: Map<String, Any?>? = null
    )

    fun sendErrorTelemetryEvent(
        message: String,
        throwable: Throwable?,
        additionalProperties: Map<String, Any?>? = null
    )

    fun sendErrorTelemetryEvent(
        message: String,
        stack: String?,
        kind: String?,
        additionalProperties: Map<String, Any?>? = null
    )

    fun sendMetricEvent(
        message: String,
        additionalProperties: Map<String, Any?>?
    )

    @Suppress("FunctionMaxLength")
    fun sendConfigurationTelemetryEvent(coreConfiguration: TelemetryCoreConfiguration)

    fun updatePerformanceMetric(metric: RumPerformanceMetric, value: Double)

    fun setSyntheticsAttribute(testId: String, resultId: String)
}
