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
    // OkHttp matches header names case-insensitively and keeps every baggage header field.
    val existing = try {
        build().headers(TracingInterceptor.W3C_BAGGAGE_KEY)
    } catch (_: IllegalStateException) {
        // build() only throws when no URL is set, which can't happen here: the builder comes from
        // request.newBuilder() of the intercepted request. Keeping the headers as they are is safe.
        return
    }
    val cleanedValues = _TraceInternalProxy.removeRetainedUserAnonymousIdBaggage(span, existing) ?: return
    removeHeader(TracingInterceptor.W3C_BAGGAGE_KEY)
    cleanedValues.forEach { addHeader(TracingInterceptor.W3C_BAGGAGE_KEY, it) }
}

// The W3C baggage specification allows several baggage headers, which must be combined in order.
internal fun Request.Builder.resolveExistingBaggageHeaderValue(): String? {
    return try {
        build().headers(TracingInterceptor.W3C_BAGGAGE_KEY)
            .reduceOrNull { acc, header -> _TraceInternalProxy.mergeBaggage(acc, header) }
    } catch (_: IllegalStateException) {
        // build() only throws when no URL is set, which can't happen for an intercepted request.
        null
    }
}
