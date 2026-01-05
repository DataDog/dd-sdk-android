/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal.net

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpan

/**
 * For internal usage only.
 *
 * Holds the tracing state for a network request.
 * This state is created by [com.datadog.android.trace.NetworkTracingInstrumentation.onRequest] and should be passed
 * to [com.datadog.android.trace.NetworkTracingInstrumentation.onResponseSucceed] or [com.datadog.android.trace.NetworkTracingInstrumentation.onResponseFailed].
 *
 * @property requestModifier the modifier for the HTTP request info, containing any added headers.
 * @property rumApmLinkingEnabled whether RUM-APM linking is enabled for this request.
 * @property isSampled whether the request trace was sampled.
 * @property span the trace span created for this request, or null if not sampled or tracing is disabled.
 * @property sampleRate the sample rate used for the sampling decision.
 */
@InternalApi
data class RequestTraceState(
    internal val requestModifier: HttpRequestInfoModifier,
    val rumApmLinkingEnabled: Boolean = true,
    internal val isSampled: Boolean = false,
    val span: DatadogSpan? = null,
    val sampleRate: Float? = null
) {

    /**
     * Returns the [HttpRequestInfo] with any modifications applied during tracing setup.
     * This includes tracing headers that were added to the original request.
     */
    val requestInfo: HttpRequestInfo
        get() = requestModifier.result()
}
