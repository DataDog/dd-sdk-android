/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.android.trace.api.trace.DatadogTraceId

interface DatadogSpanContext {
    val traceId: DatadogTraceId

    val spanId: Long

    val serviceName: String?

    val samplingPriority: Int

    val tags: Map<String?, Any?>?

    fun setSamplingPriority(samplingPriority: Int): Boolean

    fun setMetric(key: CharSequence?, value: Double)
}
