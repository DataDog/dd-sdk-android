/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.trace

import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.internal.telemetry.TracingHeaderType as TelemetryTracingHeaderType

internal fun TracingHeaderType.toTelemetryTracingHeaderType(): TelemetryTracingHeaderType {
    return when (this) {
        TracingHeaderType.DATADOG -> TelemetryTracingHeaderType.DATADOG
        TracingHeaderType.B3 -> TelemetryTracingHeaderType.B3
        TracingHeaderType.B3MULTI -> TelemetryTracingHeaderType.B3MULTI
        TracingHeaderType.TRACECONTEXT -> TelemetryTracingHeaderType.TRACECONTEXT
    }
}
