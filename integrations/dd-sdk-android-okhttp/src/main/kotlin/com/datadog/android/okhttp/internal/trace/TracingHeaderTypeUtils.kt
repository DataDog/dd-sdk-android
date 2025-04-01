/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.trace

import com.datadog.android.internal.telemetry.TracingHeaderTypesSet
import com.datadog.android.trace.TracingHeaderType

private typealias InternalTracingHeaderType = com.datadog.android.internal.telemetry.TracingHeaderType

internal fun mapHostsWithHeaderTypes(
    tracedHosts: Map<String, Set<TracingHeaderType>>
): TracingHeaderTypesSet {
    return TracingHeaderTypesSet(
        types = tracedHosts.values.fold(mutableSetOf()) { acc, headerTypes ->
            acc.apply { this += headerTypes.map(TracingHeaderType::toInternalTracingHeaderType) }
        }
    )
}

internal fun TracingHeaderType.toInternalTracingHeaderType(): InternalTracingHeaderType {
    return when (this) {
        TracingHeaderType.DATADOG -> InternalTracingHeaderType.DATADOG
        TracingHeaderType.B3 -> InternalTracingHeaderType.B3
        TracingHeaderType.B3MULTI -> InternalTracingHeaderType.B3MULTI
        TracingHeaderType.TRACECONTEXT -> InternalTracingHeaderType.TRACECONTEXT
    }
}
