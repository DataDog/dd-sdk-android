/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor

internal class Telemetry(
    private val sdkCore: SdkCore
) {
    internal val rumMonitor: AdvancedRumMonitor
        get() {
            return GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor ?: NoOpAdvancedRumMonitor()
        }
    fun error(
        message: String,
        throwable: Throwable? = null
    ) {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)
            ?.sendErrorTelemetryEvent(message, throwable)
    }

    fun error(
        message: String,
        stack: String? = null,
        kind: String? = null
    ) {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)
            ?.sendErrorTelemetryEvent(message, stack, kind)
    }

    fun debug(message: String, additionalProperties: Map<String, Any?>? = null) {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)
            ?.sendDebugTelemetryEvent(message, additionalProperties)
    }
    fun metric(message: String, additionalProperties: Map<String, Any?>? = null) {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)
            ?.sendMetricEvent(message, additionalProperties)
    }
}
