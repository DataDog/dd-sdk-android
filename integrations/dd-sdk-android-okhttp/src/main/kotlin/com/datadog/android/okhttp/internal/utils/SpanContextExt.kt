/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.utils

import com.datadog.opentracing.DDSpanContext
import io.opentracing.SpanContext

private const val TRACE_ID_REQUIRED_LENGTH = 32
private const val HEX_RADIX = 16

internal fun SpanContext.traceIdAsHexString(): String {
    return (this as? DDSpanContext)?.traceId?.toString(HEX_RADIX)?.padStart(TRACE_ID_REQUIRED_LENGTH, '0') ?: ""
}
