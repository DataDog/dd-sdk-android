/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Builder interface for creating instances of [DatadogSpan].
 * Provides methods to configure various attributes and context of a Datadog span
 * before initializing it.
 */
@NoOpImplementation
@SuppressWarnings("TooManyFunctions")
interface DatadogSpanBuilder {

    /**
     * Builds and starts a new span with the current configuration of the builder.
     *
     * @return The newly created instance of [DatadogSpan].
     */
    fun start(): DatadogSpan

    /**
     * Specifies the origin of the span.
     * The origin provides metadata about the context or source where the span was created.
     *
     * @param origin The origin string to associate with the span. Can be nullable.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withOrigin(origin: String?): DatadogSpanBuilder

    /**
     * Adds a tag to the span with the specified key and a nullable double value.
     * Tags are used to add contextual information to spans, which can later be
     * used for filtering, grouping, or analyzing spans in trace data.
     *
     * @param key The key identifying the tag. Cannot be null.
     * @param value The value associated with the tag. Can be null.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withTag(key: String, value: Double?): DatadogSpanBuilder

    /**
     * Adds a tag to the span with the specified key and a nullable long value.
     * Tags are used to add contextual information to spans, which can later be
     * used for filtering, grouping, or analyzing spans in trace data.
     *
     * @param key The key identifying the tag. Must not be null.
     * @param value The nullable long value associated with the tag.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withTag(key: String, value: Long?): DatadogSpanBuilder

    /**
     * Adds a tag to the span with the specified key and a nullable value of any type.
     * Tags are used to add contextual information to spans, which can later be
     * used for filtering, grouping, or analyzing spans in trace data.
     *
     * @param key The key identifying the tag. Cannot be null.
     * @param value The nullable value associated with the tag.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withTag(key: String, value: Any?): DatadogSpanBuilder

    /**
     * Specifies the resource name of the span.The resource name is used to describe
     * the operation or endpoint associated with the span.
     *
     * @param resourceName The resource name to associate with the span.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withResourceName(resourceName: String?): DatadogSpanBuilder

    /**
     * Sets the parent context for the span being built. Allowing to link related operations together.
     *
     * @param parentContext The parent context to associate with the span. Can be null.
     * @return The current instance of [DatadogSpanBuilder] to enable method chaining.
     */
    fun withParentContext(parentContext: DatadogSpanContext?): DatadogSpanBuilder

    /**
     * Sets the parent span for the span being built. The default implementation uses parentSpan only for
     * context retrieval.
     *
     * @param parentSpan The parent span to associate with the span being built. Can be null.
     * @return The current instance of [DatadogSpanBuilder] to enable method chaining.
     */
    fun withParentSpan(parentSpan: DatadogSpan?): DatadogSpanBuilder

    /**
     * Sets the start timestamp for the span being built, specified in microseconds.
     * This value determines the starting point of the span in the overall trace timeline.
     *
     * @param micros The start timestamp in microseconds.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withStartTimestamp(micros: Long): DatadogSpanBuilder

    /**
     * Prevents builder from using current active span as a parent context.
     *
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun ignoreActiveSpan(): DatadogSpanBuilder

    /**
     * Adds a link to another span in the context of distributed tracing.
     * This allows associating the current span with an external span,
     * enabling relationship tracking across traces.
     *
     * @param link The link to be associated with the span. Represents a relationship
     *             to another span in a different or the same trace.
     * @return The current instance of [DatadogSpanBuilder] to allow method chaining.
     */
    fun withLink(link: DatadogSpanLink): DatadogSpanBuilder
}
