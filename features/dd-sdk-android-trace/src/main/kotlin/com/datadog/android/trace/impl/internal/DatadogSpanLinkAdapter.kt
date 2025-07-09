/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.span.DatadogSpanLink
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink
import com.datadog.trace.bootstrap.instrumentation.api.SpanLink
import com.datadog.trace.bootstrap.instrumentation.api.SpanLinkAttributes

internal class DatadogSpanLinkAdapter(delegate: DatadogSpanLink) :
    SpanLink(
        /* traceId */
        DDTraceId.fromHex(DatadogTracingInternalToolkit.traceIdConverter.toHexString(delegate.traceId)),
        /* spanId */
        delegate.spanId,
        /* traceFlags */
        if (delegate.sampled) AgentSpanLink.SAMPLED_FLAG else AgentSpanLink.DEFAULT_FLAGS,
        /* traceState */
        delegate.traceStrace,
        /* attributes */
        SpanLinkAttributes.fromMap(delegate.attributes)
    )
