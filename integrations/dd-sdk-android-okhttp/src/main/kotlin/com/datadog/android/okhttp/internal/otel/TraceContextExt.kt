/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.otel

import com.datadog.android.okhttp.TraceContext
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.core.propagation.ExtractedContext

internal fun TraceContext.toAgentSpanContext(): ExtractedContext {
    return ExtractedContext(
        DDTraceId.fromHexOrDefault(traceId, DDTraceId.ZERO),
        DDSpanId.fromHexOrDefault(spanId, DDSpanId.ZERO),
        samplingPriority,
        null,
        null,
        null
    )
}
