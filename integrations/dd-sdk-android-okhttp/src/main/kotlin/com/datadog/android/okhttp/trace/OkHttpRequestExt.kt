/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import io.opentracing.Span
import okhttp3.Request

/**
 * Set the parent for the [Span] created around this OkHttp [Request].
 * @param span the parent [Span]
 */
fun Request.Builder.parentSpan(span: Span): Request.Builder {
    @Suppress("UnsafeThirdPartyFunctionCall") // Span can't be null
    tag(Span::class.java, span)
    return this
}
