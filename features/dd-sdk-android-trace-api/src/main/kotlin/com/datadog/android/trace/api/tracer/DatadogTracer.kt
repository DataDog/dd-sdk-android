/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.tracer

import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.propagation.NoOpDatadogPropagation
import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.scope.DatadogScopeListener
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.tools.annotation.NoOpImplementation

/**
 * [DatadogTracer] is a simple, thin interface for span creation and propagation across arbitrary transports.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface DatadogTracer {
    /**
     * Retrieves the currently active span in the context of the tracer.
     *
     * @return the active [DatadogSpan].
     */
    fun activeSpan(): DatadogSpan?

    /**
     * Provides an implementation of the [DatadogPropagation] interface to be used for span propagation.
     *
     * @return An instance of [DatadogPropagation]
     */
    fun propagate(): DatadogPropagation = NoOpDatadogPropagation()

    /**
     * Activates the provided span within the current context of the tracer.
     * Once activated, the span becomes the currently active span, and any operations
     * requiring an active span will use this one until it is explicitly deactivated.
     *
     * @param span The span to be activated. Represents the logical unit of work being traced.
     * @return An instance of [DatadogScope] representing the activated scope.
     */
    fun activateSpan(span: DatadogSpan): DatadogScope?

    /**
     * Activates the provided span within the current context of the tracer.
     * If `asyncPropagating` is set to true, the span is propagated asynchronously.
     * Once activated, the span becomes the currently active span until it is explicitly deactivated.
     *
     * @param span The span to be activated. Represents the logical unit of work being traced.
     * @param asyncPropagating If true, this context will propagate across async boundaries.
     * @return An instance of [DatadogScope] representing the activated scope.
     */
    fun activateSpan(span: DatadogSpan, asyncPropagating: Boolean): DatadogScope?

    /**
     * Creates a new span builder instance with the specified span name.
     *
     * @param spanName The name of the span to be built. Represents the operation being performed.
     * @return An instance of [DatadogSpanBuilder] to allow further configuration of the span.
     */
    fun buildSpan(spanName: CharSequence): DatadogSpanBuilder

    /**
     * Creates a new span builder instance with the specified instrumentation and span names.
     *
     * @param instrumentationName The name of the instrumentation associated with the span.
     * @param spanName The name of the span to be built. Represents the operation being performed.
     * @return An instance of [DatadogSpanBuilder] to allow further configuration of the span.
     */
    fun buildSpan(instrumentationName: String, spanName: CharSequence): DatadogSpanBuilder

    /**
     * Adds a listener to be notified when a scope is activated or closed.
     *
     * @param scopeListener The listener to be added. It defines the actions
     * to be executed after a scope is activated or closed.
     */
    fun addScopeListener(scopeListener: DatadogScopeListener)
}
