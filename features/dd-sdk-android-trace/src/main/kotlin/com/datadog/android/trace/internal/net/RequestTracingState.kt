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
 * This class allows binding RUM to APM.
 *
 * @property requestInfoBuilder the modifier for the HTTP request info, containing any added headers.
 * @property isSampled whether the request trace was sampled.
 * @property span the trace span created for this request, or null if not sampled or tracing is disabled.
 * @property sampleRate the sample rate used for the sampling decision.
 */
@InternalApi
data class RequestTracingState(
    val requestInfoBuilder: HttpRequestInfoBuilder,
    val isSampled: Boolean = false,
    val span: DatadogSpan? = null,
    val sampleRate: Float? = null
) {

    /**
     * Returns the [HttpRequestInfo] with any modifications applied during tracing setup.
     * This includes tracing headers that were added to the original request.
     */
    fun createRequestInfo(): HttpRequestInfo = requestInfoBuilder.build()
}

/**
 * For internal usage only.
 *
 * Converts this [RequestTracingState] into a map of RUM resource attributes
 * containing trace context (trace ID, span ID, and sampling rule PSR).
 * Returns an empty map if the state is null, the span is null, or the request was not sampled.
 *
 * @param traceIdKey the key to use for the trace ID attribute.
 * @param spanIdKey the key to use for the span ID attribute.
 * @param rulePsrKey the key to use for the rule PSR (proportional sampling rate) attribute.
 * @return a map of RUM resource attributes, or an empty map if tracing context is unavailable.
 */
@InternalApi
fun RequestTracingState?.toAttributesMap(
    traceIdKey: String,
    spanIdKey: String,
    rulePsrKey: String
): Map<String, Any?> = this?.span
    ?.takeIf { isSampled }
    ?.let { span ->
        buildMap {
            put(traceIdKey, span.context().traceId.toHexString())
            put(spanIdKey, span.context().spanId.toString())
            put(rulePsrKey, (sampleRate ?: ZERO_SAMPLE_RATE) / ALL_IN_SAMPLE_RATE)
        }
    }.orEmpty()

internal const val ZERO_SAMPLE_RATE: Float = 0.0f
internal const val ALL_IN_SAMPLE_RATE: Float = 100.0f
