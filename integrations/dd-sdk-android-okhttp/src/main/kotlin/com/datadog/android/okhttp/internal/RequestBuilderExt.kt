/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.internal._TraceInternalProxy
import okhttp3.Request

// A retried request keeps the baggage of its previous attempt, which may hold an anonymous ID
// that must not be propagated anymore and that no new baggage would replace.
internal fun Request.Builder.removeRetainedUserAnonymousIdBaggage(span: DatadogSpan) {
    val existing = try {
        build().headers[TracingInterceptor.W3C_BAGGAGE_KEY]
    } catch (_: IllegalStateException) {
        null
    }
    val stripped = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, existing)
    if (stripped == null) {
        removeHeader(TracingInterceptor.W3C_BAGGAGE_KEY)
    } else if (stripped != existing) {
        header(TracingInterceptor.W3C_BAGGAGE_KEY, stripped)
    }
}
