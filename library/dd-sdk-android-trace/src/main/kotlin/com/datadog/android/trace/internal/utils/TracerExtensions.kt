/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.utils

import com.datadog.opentracing.DDSpan
import io.opentracing.Span
import io.opentracing.Tracer

internal fun Tracer.traceId(): String? {
    val activeSpan: Span? = activeSpan()
    return if (activeSpan is DDSpan) {
        activeSpan.traceId.toString()
    } else {
        null
    }
}

internal fun Tracer.spanId(): String? {
    val activeSpan: Span? = activeSpan()
    return if (activeSpan is DDSpan) {
        activeSpan.spanId.toString()
    } else {
        null
    }
}
