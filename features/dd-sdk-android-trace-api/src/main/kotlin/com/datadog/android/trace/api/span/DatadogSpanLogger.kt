/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface for logging messages or errors associated with a DatadogSpan.
 * Provides methods for associating log messages, errors, and custom attributes with a specific span.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface DatadogSpanLogger {
    /**
     * Logs a message associated with the given span.
     *
     * @param message The log message to be associated with the span.
     * @param span The DatadogSpan to which the log message will be linked.
     */
    fun log(message: String, span: DatadogSpan)

    /**
     * Logs an error message and associates it with the specified span.
     *
     * @param message The error message to be logged.
     * @param span The DatadogSpan to which the error message will be linked.
     */
    fun logErrorMessage(message: String, span: DatadogSpan)

    /**
     * Logs an error represented by a throwable and associates it with the given span.
     *
     * @param throwable The throwable containing error details that need to be logged.
     * @param span The DatadogSpan to which the error information will be linked.
     */
    fun log(throwable: Throwable, span: DatadogSpan)

    /**
     * Logs a set of attributes and associates them with the specified DatadogSpan.
     *
     * @param attributes A map containing key-value pairs of attributes to be logged.
     *                   These attributes provide additional context or metadata for the span.
     *                   See [DatadogTracingConstants.LogAttributes] for allowed keys.
     * @param span The DatadogSpan to which the attributes will be linked.
     */
    fun log(attributes: Map<String, Any>, span: DatadogSpan)
}
