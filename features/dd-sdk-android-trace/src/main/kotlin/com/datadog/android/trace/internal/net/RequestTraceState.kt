/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal.net

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpan

/**
 * For internal usage only.
 *
 * Holds the tracing state for a network request.
 * This state is created by [com.datadog.android.trace.internal.ApmNetworkInstrumentation.onRequest] and should be passed
 * to [com.datadog.android.trace.internal.ApmNetworkInstrumentation.onResponseSucceeded] or [com.datadog.android.trace.internal.ApmNetworkInstrumentation.onResponseFailed].
 *
 * @property requestBuilder the modifier for the HTTP request info, containing any added headers.
 * @property isSampled whether the request trace was sampled.
 * @property span the trace span created for this request, or null if not sampled or tracing is disabled.
 * @property sampleRate the sample rate used for the sampling decision.
 */
@InternalApi
data class RequestTraceState(
    internal val requestBuilder: HttpRequestInfoBuilder,
    val isSampled: Boolean = false,
    val span: DatadogSpan? = null,
    val sampleRate: Float? = null
) {

    /**
     * Returns the [HttpRequestInfo] with any modifications applied during tracing setup.
     * This includes tracing headers that were added to the original request.
     */
    val requestInfo: HttpRequestInfo
        get() = requestBuilder.build()
}
