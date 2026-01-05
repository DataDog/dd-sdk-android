/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Listener for automatically created [com.datadog.android.trace.api.span.DatadogSpan] around [com.datadog.android.api.instrumentation.network.HttpRequestInfo].
 */
@NoOpImplementation
interface NetworkTracedRequestListener {
    /**
     * Notifies that a span was automatically created around an OkHttp [com.datadog.android.api.instrumentation.network.HttpRequestInfo].
     * You can update the given [com.datadog.android.trace.api.span.DatadogSpan] (e.g.: add custom tags / baggage items) before it
     * is persisted. Won't be called if [com.datadog.android.api.instrumentation.network.HttpRequestInfo] wasn't sampled.
     * @param request the intercepted [com.datadog.android.api.instrumentation.network.HttpRequestInfo]
     * @param span the [com.datadog.android.trace.api.span.DatadogSpan] created around the intercepted [com.datadog.android.api.instrumentation.network.HttpRequestInfo]
     * @param response the [com.datadog.android.api.instrumentation.network.HttpRequestInfo] response in case of any
     * @param throwable in case an error occurred during the [com.datadog.android.api.instrumentation.network.HttpRequestInfo]
     */
    fun onRequestIntercepted(
        request: HttpRequestInfo,
        span: DatadogSpan,
        response: HttpResponseInfo?,
        throwable: Throwable?
    )
}
