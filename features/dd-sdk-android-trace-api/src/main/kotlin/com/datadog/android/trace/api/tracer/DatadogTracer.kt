/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.tracer

import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DatadogSpanBuilder

interface DatadogTracer {
    fun buildSpan(spanName: CharSequence): DatadogSpanBuilder
    fun propagate(): DatadogPropagation
}
