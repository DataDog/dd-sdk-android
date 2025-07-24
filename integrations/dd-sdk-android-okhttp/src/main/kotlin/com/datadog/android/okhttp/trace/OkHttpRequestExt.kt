/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import com.datadog.android.trace.api.span.DatadogSpan
import okhttp3.Request

/**
 * Set the parent for the [DatadogSpan] created around this OkHttp [Request].
 * @param span the parent [DatadogSpan]
 */
fun Request.Builder.parentSpan(span: DatadogSpan): Request.Builder {
    @Suppress("UnsafeThirdPartyFunctionCall") // Span can't be null
    tag(DatadogSpan::class.java, span)
    return this
}
