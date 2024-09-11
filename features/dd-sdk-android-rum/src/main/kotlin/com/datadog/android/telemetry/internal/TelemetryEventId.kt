/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.internal.telemetry.TelemetryEvent

internal data class TelemetryEventId(
    val type: TelemetryType,
    val message: String,
    val kind: String?
)

internal val TelemetryEvent.identity: TelemetryEventId
    get() {
        return when (this) {
            is TelemetryEvent.Log.Error -> TelemetryEventId(type(), message, kind)
            is TelemetryEvent.Log.Debug -> TelemetryEventId(type(), message, null)
            else -> TelemetryEventId(type(), "", null)
        }
    }

internal fun TelemetryEvent.type(): TelemetryType {
    return when (this) {
        is TelemetryEvent.Log.Debug -> TelemetryType.DEBUG
        is TelemetryEvent.Log.Error -> TelemetryType.ERROR
        is TelemetryEvent.Configuration -> TelemetryType.CONFIGURATION
        is TelemetryEvent.Metric -> TelemetryType.METRIC
        is TelemetryEvent.ApiUsage -> TelemetryType.API_USAGE
        is TelemetryEvent.InterceptorInstantiated -> TelemetryType.INTERCEPTOR_SETUP
    }
}
