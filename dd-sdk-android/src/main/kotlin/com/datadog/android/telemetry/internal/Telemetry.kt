/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor

internal class Telemetry {

    internal val rumMonitor: AdvancedRumMonitor
        get() {
            return GlobalRum.get() as? AdvancedRumMonitor ?: NoOpAdvancedRumMonitor()
        }

    fun error(message: String, throwable: Throwable? = null) {
        rumMonitor.sendErrorTelemetryEvent(message, throwable)
    }

    fun error(message: String, stack: String?, kind: String?) {
        rumMonitor.sendErrorTelemetryEvent(message, stack, kind)
    }

    fun debug(message: String) {
        rumMonitor.sendDebugTelemetryEvent(message)
    }
}
