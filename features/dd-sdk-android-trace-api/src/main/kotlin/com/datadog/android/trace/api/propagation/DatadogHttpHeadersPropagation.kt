/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.propagation

import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer

/**
 * Interface for handling trace context propagation via HTTP headers.
 * Implementations of this interface are responsible for injecting trace context
 * headers into HTTP requests based on the sampling decision.
 */
interface DatadogHttpHeadersPropagation {

    /**
     * Handles HTTP header injection for sampled requests.
     * This method is called when a request has been sampled and trace context headers
     * should be injected to propagate the trace to downstream services.
     *
     * @param requestInfoBuilder the modifier to use for adding headers to the request.
     * @param tracer the tracer instance used for context propagation.
     * @param span the span associated with this request.
     * @return the modified request info builder with headers injected.
     */
    fun handleSampledHeaders(
        requestInfoBuilder: HttpRequestInfoModifier,
        tracer: DatadogTracer,
        span: DatadogSpan
    ): HttpRequestInfoModifier

    /**
     * Handles HTTP header injection for non-sampled requests.
     * This method is called when a request has not been sampled. Depending on the
     * trace context injection strategy, headers may or may not be injected.
     *
     * @param requestInfoBuilder the modifier to use for adding headers to the request.
     * @param tracer the tracer instance used for context propagation.
     * @param span the span associated with this request.
     * @return the modified request info builder with headers injected (if applicable).
     */
    fun handleNotSampledHeaders(
        requestInfoBuilder: HttpRequestInfoModifier,
        tracer: DatadogTracer,
        span: DatadogSpan
    ): HttpRequestInfoModifier
}
