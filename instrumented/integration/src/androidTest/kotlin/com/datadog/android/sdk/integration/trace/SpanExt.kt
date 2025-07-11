/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal.DatadogTracingToolkit

/**
 * Returns the span's least significant trace id in hex format (the last 64 bits from the 128 bits trace id)
 */
fun DatadogSpan.leastSignificant64BitsTraceId(): String {
    return traceId.toHexString().padStart(32, '0').takeLast(16)
}

/**
 * Returns the span's most significant trace id in hex format (the first 64 bits from the 128 bits trace id)
 */
fun DatadogSpan.mostSignificant64BitsTraceId(): String {
    return traceId.toHexString().padStart(32, '0').take(16)
}

/**
 * Returns the span's spanId in hex format.
 */
fun DatadogSpan.spanIdAsHexString(): String {
    return DatadogTracingToolkit.spanIdConverter.toHexStringPadded(context().spanId)
}
