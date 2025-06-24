/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.propagation

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext

interface DatadogPropagation {
    fun <C> inject(span: DatadogSpan, carrier: C, setter: (carrier: C, key: String, value: String) -> Unit)
    fun <C> inject(context: DatadogSpanContext, carrier: C, setter: (carrier: C, key: String, value: String) -> Unit)
    fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext?

    fun isExtractedContext(context: DatadogSpanContext): Boolean

    fun createExtractedContext(traceId: String, spanId: String, samplingPriority: Int): DatadogSpanContext
}

