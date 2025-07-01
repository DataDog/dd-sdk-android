/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface DatadogSpanLink {
    val spanId: Long
    val sampled: Boolean
    val traceId: DatadogTraceId
    val traceStrace: String
    val attributes: Map<String, String>?
}
