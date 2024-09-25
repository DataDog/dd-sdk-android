/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.internal.telemetry.InternalTelemetryEvent

internal data class TelemetryEventId(
    val type: TelemetryType,
    val message: String,
    val kind: String?
)

internal val InternalTelemetryEvent.identity: TelemetryEventId
    get() {
        return when (this) {
            is InternalTelemetryEvent.Log.Error -> TelemetryEventId(type(), message, kind)
            is InternalTelemetryEvent.Log.Debug -> TelemetryEventId(type(), message, null)
            else -> TelemetryEventId(type(), "", null)
        }
    }

internal fun InternalTelemetryEvent.type(): TelemetryType {
    return when (this) {
        is InternalTelemetryEvent.Log.Debug -> TelemetryType.DEBUG
        is InternalTelemetryEvent.Log.Error -> TelemetryType.ERROR
        is InternalTelemetryEvent.Configuration -> TelemetryType.CONFIGURATION
        is InternalTelemetryEvent.Metric -> TelemetryType.METRIC
        is InternalTelemetryEvent.ApiUsage -> TelemetryType.API_USAGE
        is InternalTelemetryEvent.InterceptorInstantiated -> TelemetryType.INTERCEPTOR_SETUP
    }
}
